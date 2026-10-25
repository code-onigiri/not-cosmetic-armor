package dev.codeonigiri.notcosmeticarmor.compat.sophisticatedbackpacks;

import dev.codeonigiri.notcosmeticarmor.config.CnaConfig;
import dev.codeonigiri.notcosmeticarmor.core.CosmeticStacks;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared helpers for the optional Sophisticated Backpacks patch.
 *
 * <p>Nothing here references a Sophisticated Backpacks class - the mod is not a compile dependency,
 * the one object that has to be identified (its {@code "armor"} inventory handler) is looked up
 * reflectively once, and only its identity is ever compared. That also keeps this file safe to load
 * when the mod is missing, which matters because {@code /notcosmeticarmor status} reports the
 * counters unconditionally.
 *
 * <p>Sophisticated Backpacks decides which backpack the player "wears" by walking its own inventory
 * handlers. The armor handler is hard wired to {@code Inventory#armor}, which this mod never writes
 * to, so a backpack placed in a cosmetic slot was invisible: pressing the open key made the client
 * send its request, the server scanned every handler, found no backpack and the request died without
 * any visible error.
 */
public final class SophisticatedBackpacksCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger("notcosmeticarmor");

    /** CAR slot index of the chest slot (0 = feet, 1 = legs, 2 = chest, 3 = head). */
    public static final int CHEST = 2;

    /** Every item of this namespace is a backpack of some tier. */
    private static final String NAMESPACE = "sophisticatedbackpacks";

    private static final String PROVIDER_CLASS = "net.p3pp3rf1y.sophisticatedbackpacks.util.PlayerInventoryProvider";
    private static final String ARMOR_HANDLER = "armor";

    /** Diagnostic counters exposed by {@code /notcosmeticarmor status}. */
    public static final AtomicLong CALLS = new AtomicLong();
    public static final AtomicLong HITS = new AtomicLong();

    private static volatile boolean resolved;
    private static volatile Object armorHandler;

    private SophisticatedBackpacksCompat() {
    }

    /** True when the patch should be applied at all. */
    public static boolean active() {
        return CnaConfig.enabled() && CnaConfig.overrideQueries() && CnaConfig.sophisticatedBackpacksCompat();
    }

    /** True for every tier of the mod's own backpack items. */
    public static boolean isBackpack(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return NAMESPACE.equals(id.getNamespace());
    }

    /**
     * @return the cosmetic chest stack when it is a backpack, otherwise {@link ItemStack#EMPTY}.
     *
     * <p>The same stack object is returned on every call (CAR's {@code ItemStackHandler} keeps it),
     * which matters because the backpack wrapper caches itself per stack identity.
     */
    public static ItemStack cosmeticBackpack(Player player) {
        CALLS.incrementAndGet();
        ItemStack cosmetic = CosmeticStacks.get(player, CHEST);
        if (!isBackpack(cosmetic)) {
            return ItemStack.EMPTY;
        }
        HITS.incrementAndGet();
        return cosmetic;
    }

    /**
     * Whether {@code handler} is the chest ({@code "armor"}) inventory handler of Sophisticated
     * Backpacks.
     *
     * <p>{@code PlayerInventoryProvider#get()} hands out one provider per physical side and creates
     * its handlers in the constructor, so the instance is stable for the lifetime of the process and
     * can be cached: after the first call this is a plain identity check.
     */
    public static boolean isArmorHandler(Object handler) {
        if (!resolved) {
            resolveArmorHandler();
        }
        Object target = armorHandler;
        return target != null && target == handler;
    }

    private static synchronized void resolveArmorHandler() {
        if (resolved) {
            return;
        }
        try {
            Class<?> providerClass = Class.forName(PROVIDER_CLASS);
            Object provider = providerClass.getMethod("get").invoke(null);
            Object found = providerClass.getMethod("getPlayerInventoryHandler", String.class)
                    .invoke(provider, ARMOR_HANDLER);
            if (found instanceof Optional<?> optional && optional.isPresent()) {
                armorHandler = optional.get();
            }
            if (armorHandler == null) {
                LOGGER.warn("[notcosmeticarmor] Sophisticated Backpacks is installed but its \"{}\" inventory"
                        + " handler could not be found - cosmetic backpacks will not be openable", ARMOR_HANDLER);
            }
        } catch (Throwable error) {
            LOGGER.warn("[notcosmeticarmor] could not hook into Sophisticated Backpacks", error);
        } finally {
            resolved = true;
        }
    }
}
