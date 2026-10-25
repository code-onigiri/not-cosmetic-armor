package dev.codeonigiri.notcosmeticarmor.compat.projecte.mixin;

import dev.codeonigiri.notcosmeticarmor.compat.projecte.ProjectECompat;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code GemArmorBase#hasAnyPiece} gates ProjectE's offensive gem abilities (chest explosion,
 * helmet zap) on the real armor slots. Widening it the same way as
 * {@link PlayerHelperMixin} makes those abilities available while the gem armor is only worn
 * cosmetically.
 */
@Pseudo
@Mixin(targets = "moze_intel.projecte.gameObjs.items.armor.GemArmorBase", remap = false)
public abstract class GemArmorBaseMixin {
    @Inject(method = "hasAnyPiece", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private static void notcosmeticarmor$hasAnyPiece(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || !ProjectECompat.active()) {
            return;
        }
        if (ProjectECompat.anyWorn(player, ProjectECompat::isGemArmor)) {
            cir.setReturnValue(true);
        }
    }
}