package dev.codeonigiri.notcosmeticarmor.core;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

/**
 * Per player bookkeeping for the cosmetic mirror.
 *
 * <p>{@link #bookkeeping} is set while vanilla is diffing equipment: during that window
 * {@code getItemBySlot} must report the <em>real</em> armor, otherwise vanilla would compare the
 * cosmetic stack with the real last-known stack, remove the wrong attribute modifiers and broadcast a
 * bogus {@code ClientboundSetEquipmentPacket} every single tick.
 */
public final class CosmeticState {
    private static final Map<ServerPlayer, CosmeticState> STATES = new WeakHashMap<>();

    public static CosmeticState of(ServerPlayer player) {
        return STATES.computeIfAbsent(player, p -> new CosmeticState());
    }

    public static CosmeticState peek(ServerPlayer player) {
        return STATES.get(player);
    }

    public static void forget(ServerPlayer player) {
        STATES.remove(player);
    }

    /** True while vanilla is allowed to see the real armor only. */
    public boolean bookkeeping;

    /** True while the tick window holds the cosmetic stacks in the real armor slots. */
    public boolean windowHeld;

    /** Real armor stacks parked while the tick window is open. */
    public final ItemStack[] windowReal = new ItemStack[CosmeticSlots.COUNT];

    /** True while {@code Player#hurtArmor} temporarily restored the real armor out of the window. */
    public boolean reopenWindow;

    /** Debug switch for the {@code /notcosmeticarmor probe} command. */
    public boolean probe;

    /** Last cosmetic stack the attribute mirror has been computed for. */
    public final ItemStack[] lastCosmetic = new ItemStack[CosmeticSlots.COUNT];

    /** Attribute modifiers currently injected for the cosmetic stack of each slot. */
    public final Multimap<Attribute, AttributeModifier>[] applied = newAppliers();

    private CosmeticState() {
        for (int i = 0; i < CosmeticSlots.COUNT; i++) {
            lastCosmetic[i] = ItemStack.EMPTY;
            windowReal[i] = ItemStack.EMPTY;
        }
    }

    @SuppressWarnings("unchecked")
    private Multimap<Attribute, AttributeModifier>[] newAppliers() {
        Multimap<Attribute, AttributeModifier>[] array = new Multimap[CosmeticSlots.COUNT];
        for (int i = 0; i < array.length; i++) {
            array[i] = ArrayListMultimap.create();
        }
        return array;
    }
}
