package dev.codeonigiri.notcosmeticarmor.mixin;

import dev.codeonigiri.notcosmeticarmor.config.CnaConfig;
import dev.codeonigiri.notcosmeticarmor.core.CosmeticDurability;
import dev.codeonigiri.notcosmeticarmor.core.CosmeticSlots;
import dev.codeonigiri.notcosmeticarmor.core.CosmeticState;
import dev.codeonigiri.notcosmeticarmor.core.CosmeticStacks;
import dev.codeonigiri.notcosmeticarmor.core.CosmeticTick;
import dev.codeonigiri.notcosmeticarmor.core.QueryGuard;
import dev.codeonigiri.notcosmeticarmor.core.RealPriority;
import dev.codeonigiri.notcosmeticarmor.core.TickWindow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
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
        if (!CnaConfig.enabled() || !CnaConfig.overrideQueries()) {
            return;
        }
        if (self.level().isClientSide()) {
            if (QueryGuard.suspended()) {
                return;
            }
        } else if (self instanceof ServerPlayer player) {
            CosmeticState state = CosmeticState.peek(player);
            if (state != null && state.bookkeeping) {
                return;
            }
        }
        ItemStack cosmetic = CosmeticStacks.get(self, slot.getIndex());
        if (cosmetic.isEmpty()) {
            return;
        }
        // Gear of a "real priority" mod (MekaSuit, ...) only works when its own stack is reported:
        // its modules ask getItemBySlot which armor is worn. See RealPriority for the rationale.
        ItemStack real = self.getInventory().armor.get(slot.getIndex());
        if (RealPriority.prefersReal(real, cosmetic)) {
            return;
        }
        cir.setReturnValue(cosmetic);
    }

    /**
     * Damage reducing enchantments (Protection, Feather Falling, ...) are computed from
     * {@code getArmorSlots()}, and {@code Player} returns the raw {@code Inventory#armor} list there -
     * it never asks {@code getItemBySlot}. The cosmetic stacks are <em>appended</em> to that list
     * (not substituted) so that both sets of enchantments add up, the same way the attribute mirror
     * adds the cosmetic armor's attributes on top of the real ones.
     */
    @Inject(method = "getArmorSlots", at = @At("RETURN"), cancellable = true)
    private void notcosmeticarmor$getArmorSlots(CallbackInfoReturnable<Iterable<ItemStack>> cir) {
        Player self = (Player) (Object) this;
        if (!CnaConfig.enabled() || !CnaConfig.overrideQueries()) {
            return;
        }
        if (self.level().isClientSide()) {
            if (QueryGuard.suspended()) {
                return;
            }
        } else if (self instanceof ServerPlayer player) {
            CosmeticState state = CosmeticState.peek(player);
            if (state != null && state.bookkeeping) {
                return;
            }
        }
        List<ItemStack> merged = null;
        for (int i = 0; i < CosmeticSlots.COUNT; i++) {
            ItemStack cosmetic = CosmeticStacks.get(self, i);
            if (cosmetic.isEmpty()) {
                continue;
            }
            if (merged == null) {
                merged = new ArrayList<>(CosmeticSlots.COUNT * 2);
                for (ItemStack real : cir.getReturnValue()) {
                    merged.add(real);
                }
            }
            merged.add(cosmetic);
        }
        if (merged != null) {
            cir.setReturnValue(merged);
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

    /**
     * The client has to tick the cosmetic stacks as well.
     *
     * <p>Real armor is ticked by {@code Inventory#tick} on both sides, and modded armor frequently
     * grants client visible abilities from {@code onArmorTick} without looking at the side (for
     * example fantasy_ending's chestplate sets {@code mayfly}). A cosmetic stack is not in
     * {@code Inventory#armor}, so without this call the client never learns it may fly even though the
     * server already granted the ability.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void notcosmeticarmor$tickEnd(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player) {
            CosmeticTick.onTickEnd(player);
        } else {
            CosmeticTick.onTickEndClient((Player) (Object) this);
        }
    }
}
