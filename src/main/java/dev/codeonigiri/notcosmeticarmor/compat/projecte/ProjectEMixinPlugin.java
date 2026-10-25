package dev.codeonigiri.notcosmeticarmor.compat.projecte;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Applies the ProjectE patch only when ProjectE is actually on the classpath.
 *
 * <p>This lives in its own mixin config ({@code notcosmeticarmor.projecte.mixins.json}) so the core
 * mod keeps no compile-time or runtime dependency on ProjectE. Mixin asks this plugin before it
 * touches a mixin class, so the classes that import ProjectE types are never loaded when the mod is
 * absent.
 */
public final class ProjectEMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("notcosmeticarmor");
    private static final String PROJECTE_PROBE = "moze_intel/projecte/utils/PlayerHelper.class";

    private boolean projectEPresent;

    @Override
    public void onLoad(String mixinPackage) {
        projectEPresent = resourceExists(PROJECTE_PROBE);
        if (projectEPresent) {
            LOGGER.info("[notcosmeticarmor] ProjectE detected - enabling the gem armor patch");
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return projectEPresent;
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

    /**
     * Looks the target up as a class file instead of loading it. Asking the class loader for the
     * class would define it before Mixin gets a chance to transform it, which then fails with
     * "target was loaded too early".
     */
    private static boolean resourceExists(String path) {
        ClassLoader loader = ProjectEMixinPlugin.class.getClassLoader();
        return loader != null && loader.getResource(path) != null;
    }
}
