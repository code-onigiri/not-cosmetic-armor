package dev.codeonigiri.notcosmeticarmor.core;

import com.google.common.collect.Multimap;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

/**
 * Layer 1: keeps a set of transient attribute modifiers in sync with the cosmetic armor.
 *
 * <p>The modifiers are never derived from {@code getItemBySlot}, so this layer keeps working even
 * while vanilla is diffing equipment. Their UUIDs are remapped per player and slot, because vanilla
 * armor modifiers use fixed per-slot UUIDs ({@code Item.BASE_ATTRIBUTE_MODIFIER_UUID_PER_SLOT}) and a
 * cosmetic helmet would otherwise collide with the real helmet instead of adding to it.
 */
public final class AttributeMirror {
    private AttributeMirror() {
    }

    public static void tick(ServerPlayer player, CosmeticState state) {
        AttributeMap attributes = player.getAttributes();
        for (int i = 0; i < CosmeticSlots.COUNT; i++) {
            ItemStack cosmetic = CosmeticStacks.get(player, i);
            if (ItemStack.matches(cosmetic, state.lastCosmetic[i])) {
                continue;
            }
            removeSlot(player, state, i);
            if (!cosmetic.isEmpty()) {
                applySlot(player, state, i, cosmetic);
            }
            state.lastCosmetic[i] = cosmetic.copy();
        }
    }

    private static void applySlot(ServerPlayer player, CosmeticState state, int slot, ItemStack cosmetic) {
        AttributeMap attributes = player.getAttributes();
        EquipmentSlot equipmentSlot = CosmeticSlots.byIndex(slot);
        Multimap<Attribute, AttributeModifier> modifiers = cosmetic.getAttributeModifiers(equipmentSlot);
        if (modifiers.isEmpty()) {
            return;
        }
        int index = 0;
        for (Map.Entry<Attribute, AttributeModifier> entry : modifiers.entries()) {
            AttributeInstance instance = attributes.getInstance(entry.getKey());
            if (instance == null) {
                continue;
            }
            AttributeModifier remapped = remap(entry.getValue(), player, slot, index++);
            instance.addTransientModifier(remapped);
            state.applied[slot].put(entry.getKey(), remapped);
        }
    }

    private static void removeSlot(ServerPlayer player, CosmeticState state, int slot) {
        Multimap<Attribute, AttributeModifier> applied = state.applied[slot];
        if (applied.isEmpty()) {
            return;
        }
        AttributeMap attributes = player.getAttributes();
        List<Map.Entry<Attribute, AttributeModifier>> entries = new ArrayList<>(applied.entries());
        for (Map.Entry<Attribute, AttributeModifier> entry : entries) {
            AttributeInstance instance = attributes.getInstance(entry.getKey());
            if (instance != null) {
                instance.removeModifier(entry.getValue().getId());
            }
        }
        applied.clear();
    }

    /** Removes every modifier this mod ever added for the player. */
    public static void clear(ServerPlayer player, CosmeticState state) {
        for (int i = 0; i < CosmeticSlots.COUNT; i++) {
            removeSlot(player, state, i);
            state.lastCosmetic[i] = ItemStack.EMPTY;
        }
    }

    private static AttributeModifier remap(AttributeModifier source, ServerPlayer player, int slot, int index) {
        String key = "notcosmeticarmor:" + player.getUUID() + ":" + slot + ":" + index + ":"
                + source.getName() + ":" + source.getAmount() + ":" + source.getOperation().toValue();
        UUID id = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
        return new AttributeModifier(id, source.getName(), source.getAmount(), source.getOperation());
    }
}
