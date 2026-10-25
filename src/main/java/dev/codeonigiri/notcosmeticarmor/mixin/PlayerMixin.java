package dev.codeonigiri.notcosmeticarmor.mixin;

import dev.codeonigiri.notcosmeticarmor.config.CnaConfig;
import dev.codeonigiri.notcosmeticarmor.core.CosmeticDurability;
import dev.codeonigiri.notcosmeticarmor.core.CosmeticState;
import dev.codeonigiri.notcosmeticarmor.core.CosmeticStacks;
import dev.codeonigiri.notcosmeticarmor.core.CosmeticTick;
import dev.codeonigiri.notcosmeticarmor.core.TickWindow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Layer 3 (query override) and the tick driver.
 *
 * <p>Reporting the cosmetic stack from {@code getItemBySlot} is what makes enchantments such as
 * Protection, Thorns or Frost Walker - and any other mod asking "what armor is this player wearing" -
 * see the cosmetic armor. The real stack keeps living in {@code Inventory#armor}: this override is a
 * pure read view, nothing is ever written back.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "getItemBySlot", at = @At("RETURN"), cancellable = true)
    private void notcosmeticarmor$getItemBySlot(EquipmentSlot slot, CallbackInfoReturnable<ItemStack> cir) {
        if (slot.getType() != EquipmentSlot.Type.ARMOR) {
            return;
        }
        Player self = (Player) (Object) this;
        if (self.level().isClientSide() || !(self instanceof ServerPlayer player)) {
            return;
        }
        if (!CnaConfig.enabled() || !CnaConfig.overrideQueries()) {
            return;
        }
        CosmeticState state = CosmeticState.of(player);
        if (state.bookkeeping) {
            return;
        }
        ItemStack cosmetic = CosmeticStacks.get(player, slot.getIndex());
        if (!cosmetic.isEmpty()) {
            cir.setReturnValue(cosmetic);
        }
    }

    /**
     * Real armor has to be in place while vanilla applies durability damage, otherwise the tick
     * window would hand {@code Inventory#hurtArmor} the temporary cosmetic copies and the damage
     * would be thrown away. The window is restored as soon as {@code hurtArmor} returns.
     */
    @Inject(method = "hurtArmor", at = @At("HEAD"))
    private void notcosmeticarmor$hurtArmorStart(DamageSource source, float amount, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayer player)) {
            return;
        }
        CosmeticState state = CosmeticState.peek(player);
        if (state != null && state.windowHeld) {
            TickWindow.close(player, state);
            state.reopenWindow = true;
        }
        CosmeticDurability.hurtArmor(player, source, amount);
    }

    @Inject(method = "hurtArmor", at = @At("RETURN"))
    private void notcosmeticarmor$hurtArmorEnd(DamageSource source, float amount, CallbackInfo ci) {
        if (!((Object) this instanceof ServerPlayer player)) {
            return;
        }
        CosmeticState state = CosmeticState.peek(player);
        if (state != null && state.reopenWindow) {
            state.reopenWindow = false;
            TickWindow.open(player, state);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void notcosmeticarmor$tickStart(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            CosmeticTick.onTickStart(player);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void notcosmeticarmor$tickEnd(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            CosmeticTick.onTickEnd(player);
        }
    }
}
