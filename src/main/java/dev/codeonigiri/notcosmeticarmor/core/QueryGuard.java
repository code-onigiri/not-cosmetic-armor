package dev.codeonigiri.notcosmeticarmor.core;

/**
 * Temporarily suppresses the query override while the client is drawing a player.
 *
 * <p>Cosmetic Armor Reworked renders cosmetics by swapping them into {@code Inventory#armor} for the
 * duration of {@code RenderPlayerEvent} (see its {@code PlayerRenderHandler}). Our own read view would
 * then answer with the same stacks a second time, and - worse - it would resurrect a slot the player
 * explicitly hid with CAR's "skin armor" toggle. Suspending the override during that window leaves
 * rendering exactly as CAR intends it.
 *
 * <p>The flag is only ever written from the client render thread; the server side never sees it set.
 */
public final class QueryGuard {
    private static boolean suspended;

    private QueryGuard() {
    }

    public static boolean suspended() {
        return suspended;
    }

    public static void suspend() {
        suspended = true;
    }

    public static void resume() {
        suspended = false;
    }
}
