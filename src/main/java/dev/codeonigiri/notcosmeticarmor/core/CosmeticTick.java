package dev.codeonigiri.notcosmeticarmor.core;

import dev.codeonigiri.notcosmeticarmor.config.CnaConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Drives the per tick work from the {@code Player#tick} injection points. */
public final class CosmeticTick {
    private CosmeticTick() {
    }

    public static void onTickStart(ServerPlayer player) {
        CosmeticState state = CosmeticState.of(player);
        // A window that never got closed (crash inside the previous tick) must not leak cosmetics.
        TickWindow.close(player, state);
        state.bookkeeping = false;
        if (!CnaConfig.enabled()) {
            AttributeMirror.clear(player, state);
            return;
        }
        if (CnaConfig.mirrorAttributes()) {
            AttributeMirror.tick(player, state);
        } else {
            AttributeMirror.clear(player, state);
        }
        TickWindow.open(player, state);
    }

    public static void onTickEnd(ServerPlayer player) {
        CosmeticState state = CosmeticState.of(player);
        TickWindow.close(player, state);
        state.bookkeeping = false;
        if (!CnaConfig.enabled() || !CnaConfig.tickCosmeticItems()) {
            return;
        }
        CosmeticItemTicker.tick(player);
    }

    /**
     * Client counterpart of {@link #onTickEnd(ServerPlayer)}.
     *
     * <p>Only the item ticks are mirrored; the attribute mirror and the swap window stay server side
     * because attributes and inventory contents are owned by the server and are already synced.
     */
    public static void onTickEndClient(Player player) {
        if (!CnaConfig.enabled() || !CnaConfig.tickCosmeticItems()) {
            return;
        }
        CosmeticItemTicker.tick(player);
    }
}
