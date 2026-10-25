package dev.codeonigiri.notcosmeticarmor.core;

import net.minecraft.world.entity.EquipmentSlot;

/** Maps Cosmetic Armor Reworked's slot index (0..3) to the vanilla armor slot order. */
public final class CosmeticSlots {
    public static final int COUNT = 4;

    /** CAR slot order: 0 = feet, 1 = legs, 2 = chest, 3 = head. */
    public static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD
    };

    private CosmeticSlots() {
    }

    public static EquipmentSlot byIndex(int index) {
        return SLOTS[index];
    }
}
