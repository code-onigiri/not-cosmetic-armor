package dev.codeonigiri.notcosmeticarmor.compat.endinglib;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorMaterial;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Read-only probes into EndingLib / fantasy_ending. Everything is reflective: those classes are absent
 * unless the player installed the mods, and a hard reference would crash the mod on a stock pack.
 */
public final class EndingLibCompat {
    private static boolean looked;
    private static boolean present;

    private static Method getArmorSet;
    private static Method is4Armor;
    private static Method getCapability;
    private static Object feShieldCapability;
    private static Method getLevel;
    private static Method getDamageResistance;

    private EndingLibCompat() {
    }

    private static synchronized void init() {
        if (looked) {
            return;
        }
        looked = true;
        try {
            Class<?> utils = Class.forName("com.mega.endinglib.util.mc.entity.armor.ArmorUtils");
            getArmorSet = utils.getMethod("getArmorSet", LivingEntity.class);

            Class<?> caps = Class.forName("com.mega.uom.common.capability.ModCapabilities");
            Field field = caps.getField("FE_SHIELD_EC");
            feShieldCapability = field.get(null);
            getCapability = caps.getMethod("getCapability", net.minecraft.world.entity.Entity.class,
                    net.minecraftforge.common.capabilities.Capability.class);

            Class<?> armor = Class.forName("com.mega.uom.common.items.armor.FantasyEndingArmorItem");
            is4Armor = armor.getMethod("is4Armor", LivingEntity.class);
            present = true;
        } catch (Throwable t) {
            present = false;
        }
    }

    public static boolean available() {
        init();
        return present;
    }

    /** The material EndingLib thinks the entity is wearing as a full set, or null. */
    public static ArmorMaterial set(Player player) {
        init();
        if (!present) {
            return null;
        }
        try {
            return (ArmorMaterial) getArmorSet.invoke(null, player);
        } catch (Throwable t) {
            return null;
        }
    }

    /** fantasy_ending's own full-set predicate (4 pieces of its material). */
    public static boolean is4Set(Player player) {
        init();
        if (!present) {
            return false;
        }
        try {
            return (Boolean) is4Armor.invoke(null, player);
        } catch (Throwable t) {
            return false;
        }
    }

    /** The synced FE shield capability: level 4936 means the full set turned it on. */
    public static String shield(Player player) {
        init();
        if (!present) {
            return "n/a";
        }
        try {
            Object cap = getCapability.invoke(null, player, feShieldCapability);
            if (cap == null) {
                return "none";
            }
            if (getLevel == null) {
                getLevel = cap.getClass().getMethod("getLevel");
            }
            if (getDamageResistance == null) {
                getDamageResistance = cap.getClass().getMethod("getDamageResistance");
            }
            return "level=" + getLevel.invoke(cap) + " dr=" + getDamageResistance.invoke(cap);
        } catch (Throwable t) {
            return "error " + t.getClass().getSimpleName();
        }
    }

    public static String describe(Player player) {
        if (!available()) {
            return "absent";
        }
        ArmorMaterial material = set(player);
        return "set=" + (material == null ? "none" : material.toString())
                + " is4=" + is4Set(player)
                + " shield[" + shield(player) + "]";
    }
}
