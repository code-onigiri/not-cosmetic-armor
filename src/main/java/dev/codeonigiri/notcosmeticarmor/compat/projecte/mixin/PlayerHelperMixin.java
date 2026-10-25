package dev.codeonigiri.notcosmeticarmor.compat.projecte.mixin;

import dev.codeonigiri.notcosmeticarmor.compat.projecte.ProjectECompat;
import java.util.function.Predicate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ProjectE asks {@code PlayerHelper#checkArmorHotbarCurios} whether the player "wears" a piece with
 * a given property. That method walks {@code Inventory#armor}, which this mod never writes to, so
 * gem boots in a cosmetic slot could not enable flight or step assist.
 *
 * <p>The injection only ever widens the answer: if ProjectE already found a matching stack in the
 * real armor, hotbar, offhand or curios, the result is left alone. Otherwise the armor slots are
 * asked through {@code Player#getItemBySlot}, which is the same call {@code GemFeet} uses for its
 * own identity check. Slots whose cosmetic stack cannot stand in for a worn one (the query
 * overrides are off, cosmetic armor is disabled, ...) simply report the real armor, so there is
 * nothing to add in that case either.
 */
@Pseudo
@Mixin(targets = "moze_intel.projecte.utils.PlayerHelper", remap = false)
public abstract class PlayerHelperMixin {
    @Inject(method = "checkArmorHotbarCurios", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void notcosmeticarmor$checkCosmeticArmor(Player player, Predicate<ItemStack> checker,
            CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || !ProjectECompat.active()) {
            return;
        }
        if (ProjectECompat.anyWorn(player, checker)) {
            cir.setReturnValue(true);
        }
    }
}
