package dev.codeonigiri.notcosmeticarmor.core;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Ticks cosmetic stacks like worn armor.
 *
 * <p>Vanilla only ticks the {@code Inventory#armor} list, so a cosmetic chestplate never sees
 * {@code inventoryTick}/{@code onArmorTick} and modded armor (energy, charge, timers) would stay inert.
 * Forge's {@code IForgeItem#onInventoryTick} accepts a global slot index, and passing
 * {@code items.size() + armorIndex} makes it take the armor branch, including the Forge
 * {@code onArmorTick} hook.
 *
 * <p>This runs on both sides on purpose. Vanilla ticks real armor on the client as well
 * ({@code Player#tick} calls {@code Inventory#tick}), and several mods rely on it: fantasy_ending's
 * chestplate grants {@code mayfly} from its armor tick without checking the side, so a cosmetic copy
 * that is only ticked on the server leaves the client unable to start flying even though the server
 * already allowed it.
 *
 * <p>Moving items (jetpacks, propulsion, ...) call {@code setDeltaMovement} from their tick. For worn
 * armor the client predicts that movement itself, but a cosmetic stack only exists on the server, so
 * the change has to be shipped to its owner explicitly. Marking the player as hurt makes
 * {@code ServerEntity} send a {@code ClientboundSetEntityMotionPacket} to the owner as well (see
 * {@code ServerEntity#broadcastAndSend}), which applies the motion on the client.
 */
public final class CosmeticItemTicker {
    private CosmeticItemTicker() {
    }

    public static void tick(Player player) {
        int armorBase = player.getInventory().items.size();
        boolean server = player instanceof ServerPlayer;
        for (int i = 0; i < CosmeticSlots.COUNT; i++) {
            ItemStack cosmetic = CosmeticStacks.get(player, i);
            if (cosmetic.isEmpty()) {
                continue;
            }
            Vec3 before = player.getDeltaMovement();
            cosmetic.getItem().onInventoryTick(cosmetic, player.level(), player, armorBase + i, -1);
            if (server && !player.getDeltaMovement().equals(before)) {
                player.hurtMarked = true;
            }
        }
    }
}
