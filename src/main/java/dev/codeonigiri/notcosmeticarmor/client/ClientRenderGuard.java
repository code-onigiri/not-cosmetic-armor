package dev.codeonigiri.notcosmeticarmor.client;

import dev.codeonigiri.notcosmeticarmor.NotCosmeticArmor;
import dev.codeonigiri.notcosmeticarmor.core.QueryGuard;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Keeps our read view out of the way while Cosmetic Armor Reworked is drawing a player.
 *
 * <p>CAR swaps the cosmetic stacks into {@code Inventory#armor} itself for the duration of a render
 * pass (registered at {@link EventPriority#HIGH} on the pre event, and restores at
 * {@link EventPriority#LOW} on the post event). Running at {@link EventPriority#LOWEST} puts us after
 * both, so the window we observe is exactly the window CAR set up.
 */
@Mod.EventBusSubscriber(
        modid = NotCosmeticArmor.MOD_ID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientRenderGuard {
    private ClientRenderGuard() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        // A cancelled pre render means the matching post event will never arrive; do not latch the flag.
        if (event.isCanceled()) {
            QueryGuard.resume();
            return;
        }
        QueryGuard.suspend();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        QueryGuard.resume();
    }
}
