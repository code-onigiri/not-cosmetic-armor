package dev.codeonigiri.notcosmeticarmor.core;

import dev.codeonigiri.notcosmeticarmor.NotCosmeticArmor;
import dev.codeonigiri.notcosmeticarmor.config.CnaConfig;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Layer 2: the tick window.
 *
 * <p>On every odd game tick the cosmetic stacks are copied into the real armor slots for the duration
 * of {@code Player#tick}, and the original stacks are put back at the end of that tick. Inside the
 * window every vanilla and mod query - {@code getItemBySlot}, elytra checks, turtle helmet checks,
 * enchantment hooks, ... - sees the cosmetic armor, while nothing is ever sent to the client: the
 * equipment diff is suppressed by {@link CosmeticState#windowHeld} (see {@code LivingEntityMixin}).
 *
 * <p>Only copies are installed, so damage taken by a temporary stack inside the window is discarded;
 * the stack stored by Cosmetic Armor Reworked itself is never mutated.
 */
public final class TickWindow {
    private TickWindow() {
    }

    /** @return true when the current tick should hold the cosmetic stacks. */
    public static boolean shouldHold(ServerPlayer player) {
        return CnaConfig.enabled() && CnaConfig.interleave() && (player.level().getGameTime() & 1L) == 1L;
    }

    public static void open(ServerPlayer player, CosmeticState state) {
        if (state.windowHeld || !shouldHold(player)) {
            return;
        }
        List<ItemStack> armor = player.getInventory().armor;
        for (int i = 0; i < CosmeticSlots.COUNT; i++) {
            ItemStack real = armor.get(i);
            state.windowReal[i] = real;
            ItemStack cosmetic = CosmeticStacks.get(player, i);
            if (!cosmetic.isEmpty() && !ItemStack.matches(cosmetic, real)) {
                armor.set(i, cosmetic.copy());
            }
        }
        state.windowHeld = true;
        // Hide the cosmetic stack from the vanilla equipment diff for the whole window.
        state.bookkeeping = true;
        if (state.probe && player.level().getGameTime() % 20L == 1L) {
            NotCosmeticArmor.LOGGER.info("[Not Cosmetic Armor] probe(inside window): chest seen={} storedReal={} armorValue={}",
                    player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST),
                    state.windowReal[2],
                    player.getArmorValue());
        }
    }

    /** Restores the real armor. Safe to call outside a window. */
    public static void close(ServerPlayer player, CosmeticState state) {
        if (!state.windowHeld) {
            return;
        }
        List<ItemStack> armor = player.getInventory().armor;
        for (int i = 0; i < CosmeticSlots.COUNT; i++) {
            armor.set(i, state.windowReal[i]);
            state.windowReal[i] = ItemStack.EMPTY;
        }
        state.windowHeld = false;
        state.bookkeeping = false;
        if (state.probe && player.level().getGameTime() % 20L == 0L) {
            NotCosmeticArmor.LOGGER.info("[Not Cosmetic Armor] probe(outside window): chest seen={} armorValue={}",
                    player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST),
                    player.getArmorValue());
        }
    }
}
