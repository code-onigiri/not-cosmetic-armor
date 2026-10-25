package dev.codeonigiri.notcosmeticarmor.core;

import dev.codeonigiri.notcosmeticarmor.car.CarBridge;
import dev.codeonigiri.notcosmeticarmor.config.CnaConfig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Reads the cosmetic stacks that should act as the worn armor of a player. */
public final class CosmeticStacks {
    private CosmeticStacks() {
    }

    /**
     * @return the cosmetic stack for {@code slot} (0..3) that should be treated as worn, or
     *     {@link ItemStack#EMPTY}.
     *
     * <p>Works on both sides. The server reads CAR's real inventory; the client reads the mirror that
     * CAR fills from its sync packets. Client side answers matter because a lot of gameplay - air
     * supply, movement enchantments, and every mod that inspects the armor slots while ticking on the
     * client - is computed locally, and would otherwise silently disagree with the server.
     */
    public static ItemStack get(Player player, int slot) {
        if (!CnaConfig.enabled() || !CarBridge.available()) {
            return ItemStack.EMPTY;
        }
        boolean client = player.level().isClientSide();
        if (!CnaConfig.ignoreSkinFlag() && CarBridge.isSkinArmor(player.getUUID(), slot, client)) {
            return ItemStack.EMPTY;
        }
        return CarBridge.get(player.getUUID(), slot, client);
    }
}
