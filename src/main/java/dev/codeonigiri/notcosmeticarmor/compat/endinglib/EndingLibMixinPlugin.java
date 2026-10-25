package dev.codeonigiri.notcosmeticarmor.compat.endinglib;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Applies the EndingLib compatibility mixin only when EndingLib is installed.
 *
 * <p>EndingLib is a hard dependency of fantasy_ending but not of this mod, so the patch lives in its own
 * mixin config and is skipped entirely when the library is absent.
 */
public final class EndingLibMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("notcosmeticarmor");
    private static final String ENDINGLIB_PROBE =
            "com/mega/endinglib/util/mc/entity/armor/ArmorUtils.class";

    private boolean endingLibPresent;

    @Override
    public void onLoad(String mixinPackage) {
        endingLibPresent = resourceExists(ENDINGLIB_PROBE);
        if (endingLibPresent) {
            LOGGER.info("[notcosmeticarmor] EndingLib detected - enabling the armor set union patch");
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return endingLibPresent;
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
        ClassLoader loader = EndingLibMixinPlugin.class.getClassLoader();
        return loader != null && loader.getResource(path) != null;
    }
}
