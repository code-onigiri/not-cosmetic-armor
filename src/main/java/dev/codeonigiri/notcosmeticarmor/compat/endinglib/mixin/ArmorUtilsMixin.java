package dev.codeonigiri.notcosmeticarmor.compat.endinglib.mixin;

import dev.codeonigiri.notcosmeticarmor.compat.endinglib.ArmorSetUnion;
import dev.codeonigiri.notcosmeticarmor.config.CnaConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorMaterial;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes EndingLib's set detection see the real armor as well as the cosmetic layer.
 *
 * <p>See {@link ArmorSetUnion} for the reasoning. This runs for every living entity tick (the result is
 * cached in fantasy_ending's per entity data), so it only walks eight stacks and returns early when no
 * cosmetics are in play.
 */
@Pseudo
@Mixin(targets = "com.mega.endinglib.util.mc.entity.armor.ArmorUtils", remap = false)
public abstract class ArmorUtilsMixin {
    @Inject(method = "getArmorSet", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void notcosmeticarmor$unionWithRealArmor(
            LivingEntity living, CallbackInfoReturnable<ArmorMaterial> cir) {
        if (!CnaConfig.enabled() || !CnaConfig.overrideQueries()) {
            return;
        }
        ArmorMaterial material = ArmorSetUnion.find(living);
        if (material != null) {
            cir.setReturnValue(material);
        }
    }
}
