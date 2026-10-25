package dev.codeonigiri.notcosmeticarmor.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Ticks cosmetic stacks like worn armor.
 *
 * <p>Vanilla only ticks the {@code Inventory#armor} list, so a cosmetic chestplate never sees
 * {@code inventoryTick}/{@code onArmorTick} and modded armor (energy, charge, timers) would stay inert.
 * Forge's {@code IForgeItem#onInventoryTick} accepts a global slot index, and passing
 * {@code items.size() + armorIndex} makes it take the armor branch, including the Forge
 * {@code onArmorTick} hook.
 */
public final class CosmeticItemTicker {
    private CosmeticItemTicker() {
    }

    public static void tick(ServerPlayer player) {
        int armorBase = player.getInventory().items.size();
        for (int i = 0; i < CosmeticSlots.COUNT; i++) {
            ItemStack cosmetic = CosmeticStacks.get(player, i);
            if (cosmetic.isEmpty()) {
                continue;
            }
            cosmetic.getItem().onInventoryTick(cosmetic, player.level(), player, armorBase + i, -1);
        }
    }
}
