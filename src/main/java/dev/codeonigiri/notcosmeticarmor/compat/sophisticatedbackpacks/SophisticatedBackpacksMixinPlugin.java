package dev.codeonigiri.notcosmeticarmor.compat.sophisticatedbackpacks;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Applies the Sophisticated Backpacks patch only when the mod is actually on the classpath.
 *
 * <p>This lives in its own mixin config ({@code notcosmeticarmor.sophisticatedbackpacks.mixins.json})
 * so the core mod keeps no compile-time or runtime dependency on it. Mixin asks this plugin before it
 * touches a mixin class, so classes that reference Sophisticated Backpacks types are never loaded
 * when the mod is absent.
 */
public final class SophisticatedBackpacksMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("notcosmeticarmor");
    private static final String PROBE = "net/p3pp3rf1y/sophisticatedbackpacks/util/PlayerInventoryHandler.class";

    private boolean present;

    @Override
    public void onLoad(String mixinPackage) {
        present = resourceExists(PROBE);
        if (present) {
            LOGGER.info("[notcosmeticarmor] Sophisticated Backpacks detected - cosmetic backpacks can be worn");
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return present;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    private static boolean resourceExists(String path) {
        ClassLoader loader = SophisticatedBackpacksMixinPlugin.class.getClassLoader();
        return loader != null && loader.getResource(path) != null;
    }
}
