package dev.codeonigiri.notcosmeticarmor.compat.sophisticatedbackpacks.mixin;

import dev.codeonigiri.notcosmeticarmor.compat.sophisticatedbackpacks.SophisticatedBackpacksCompat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes a backpack worn in a cosmetic slot look like the worn chest stack to Sophisticated Backpacks.
 *
 * <p>{@code PlayerInventoryHandler#getStackInSlot} is the single place every consumer goes through
 * ({@code runOnBackpacks} for the open key and the "open backpack" button, {@code
 * getBackpackFromRendered} for the model on the player's back, and {@code BackpackContext.Item
 * #getBackpackWrapper} when the server and the client build the menu), so one injection covers all of
 * them and keeps them consistent with each other.
 *
 * <p>The patch only ever fills a gap: a real backpack in the chest slot keeps winning, and the
 * substitution only happens for the {@code "armor"} handler, i.e. the chest slot.
 */
@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryHandler", remap = false)
public abstract class PlayerInventoryHandlerMixin {
    @Inject(method = "getStackInSlot", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void notcosmeticarmor$cosmeticBackpack(Player player, String identifier, int slot,
            CallbackInfoReturnable<ItemStack> cir) {
        if (!SophisticatedBackpacksCompat.active() || !SophisticatedBackpacksCompat.isArmorHandler(this)) {
            return;
        }
        if (SophisticatedBackpacksCompat.isBackpack(cir.getReturnValue())) {
            return;
        }
        ItemStack cosmetic = SophisticatedBackpacksCompat.cosmeticBackpack(player);
        if (!cosmetic.isEmpty()) {
            cir.setReturnValue(cosmetic);
        }
    }
}
