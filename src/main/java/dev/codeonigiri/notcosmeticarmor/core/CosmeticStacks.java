package dev.codeonigiri.notcosmeticarmor.core;

import dev.codeonigiri.notcosmeticarmor.car.CarBridge;
import dev.codeonigiri.notcosmeticarmor.config.CnaConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Reads the cosmetic stacks that should act as the worn armor of a player. */
public final class CosmeticStacks {
    private CosmeticStacks() {
    }

    /** @return the cosmetic stack for {@code slot} (0..3) that should be treated as worn, or {@link ItemStack#EMPTY}. */
    public static ItemStack get(ServerPlayer player, int slot) {
        if (!CnaConfig.enabled() || !CarBridge.available()) {
            return ItemStack.EMPTY;
        }
        if (!CnaConfig.ignoreSkinFlag() && CarBridge.isSkinArmor(player.getUUID(), slot)) {
            return ItemStack.EMPTY;
        }
        return CarBridge.get(player.getUUID(), slot);
    }
}
