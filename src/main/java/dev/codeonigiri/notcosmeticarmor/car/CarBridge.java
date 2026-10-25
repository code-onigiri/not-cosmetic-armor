package dev.codeonigiri.notcosmeticarmor.car;

import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;

/**
 * Reflection bridge to Cosmetic Armor Reworked.
 *
 * <p>CAR is distributed through CurseForge/GitHub only and publishes no maven artifact, so a compile
 * time dependency would mean shipping a jar inside the repository. Instead we call the two stable
 * entry points through reflection:
 *
 * <pre>
 *   lain.mods.cos.api.CosArmorAPI#getCAStacks(UUID)   -> lain.mods.cos.api.CAStacksBase
 *   CAStacksBase#getStackInSlot(int)                  (slot 0..3 = feet, legs, chest, head)
 *   CAStacksBase#isSkinArmor(int)
 * </pre>
 *
 * <p>{@code CAStacksBase} extends Forge's {@code ItemStackHandler}, therefore the slot accessors are
 * plain public methods and are safe to call reflectively.
 *
 * <p>Lookup uses the thread context class loader first. On Forge 1.20.1 the single argument
 * {@link Class#forName(String)} variant resolves through the mod's own transforming loader, which
 * refuses packages owned by another mod ("ClassNotFoundException: lain.mods.cos.api.CAStacksBase"),
 * while the mod loader installed as the context class loader does see them.
 */
public final class CarBridge {
    private static final String MOD_ID = "cosmeticarmorreworked";
    private static final String API_CLASS = "lain.mods.cos.api.CosArmorAPI";
    private static final String STACKS_CLASS = "lain.mods.cos.api.inventory.CAStacksBase";

    private static boolean initialized;
    private static Method getCAStacks;
    private static Method getStackInSlot;
    private static Method isSkinArmor;

    private CarBridge() {
    }

    private static Class<?> lookup(String name) throws ClassNotFoundException {
        ClassNotFoundException failure = null;
        ClassLoader[] candidates = {
            Thread.currentThread().getContextClassLoader(),
            CarBridge.class.getClassLoader(),
            CarBridge.class.getClassLoader() == null ? null : ClassLoader.getSystemClassLoader(),
        };
        for (ClassLoader loader : candidates) {
            if (loader == null) {
                continue;
            }
            try {
                return Class.forName(name, false, loader);
            } catch (Throwable t) {
                try {
                    return loader.loadClass(name);
                } catch (Throwable ignored) {
                    if (failure == null) {
                        failure = new ClassNotFoundException(name, t);
                    }
                }
            }
        }
        throw failure != null ? failure : new ClassNotFoundException(name);
    }

    private static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            Class<?> api = lookup(API_CLASS);
            Class<?> base = lookup(STACKS_CLASS);
            getCAStacks = api.getMethod("getCAStacks", UUID.class);
            getStackInSlot = base.getMethod("getStackInSlot", int.class);
            try {
                isSkinArmor = base.getMethod("isSkinArmor", int.class);
            } catch (NoSuchMethodException ignored) {
                isSkinArmor = null;
            }
        } catch (Throwable t) {
            getCAStacks = null;
            getStackInSlot = null;
            isSkinArmor = null;
        }
    }

    public static boolean available() {
        init();
        return getCAStacks != null && getStackInSlot != null;
    }

    /** @return the raw cosmetic stack for {@code slot} (0..3), or {@link ItemStack#EMPTY}. */
    public static ItemStack get(UUID playerId, int slot) {
        init();
        if (getCAStacks == null) {
            return ItemStack.EMPTY;
        }
        try {
            Object stacks = getCAStacks.invoke(null, playerId);
            if (stacks == null) {
                return ItemStack.EMPTY;
            }
            Object stack = getStackInSlot.invoke(stacks, slot);
            return stack instanceof ItemStack itemStack ? itemStack : ItemStack.EMPTY;
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    /** Diagnostic dump of the CAR inventory; used by {@code /notcosmeticarmor status}. */
    public static String describe(UUID playerId) {
        init();
        if (getCAStacks == null) {
            return "n/a";
        }
        try {
            Object stacks = getCAStacks.invoke(null, playerId);
            if (stacks == null) {
                return "null";
            }
            StringBuilder out = new StringBuilder(stacks.getClass().getSimpleName());
            try {
                out.append(" size=").append(stacks.getClass().getMethod("getSlots").invoke(stacks));
            } catch (Throwable ignored) {
                out.append(" size=?");
            }
            for (int i = 0; i < 4; i++) {
                out.append(" [").append(i).append("]=").append(getStackInSlot.invoke(stacks, i));
            }
            return out.toString();
        } catch (Throwable t) {
            return "error " + t;
        }
    }

    public static boolean isSkinArmor(UUID playerId, int slot) {
        init();
        if (getCAStacks == null || isSkinArmor == null) {
            return false;
        }
        try {
            Object stacks = getCAStacks.invoke(null, playerId);
            if (stacks == null) {
                return false;
            }
            Object result = isSkinArmor.invoke(stacks, slot);
            return result instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }
}
