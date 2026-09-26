package net.maric.colonytweaks.mixin;

import net.maric.colonytweaks.compat.MixinCompatibility;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin config plugin for {@code mariccolonytweaks.mixins.json}.
 *
 * <p>Its only job is {@link #shouldApplyMixin}: MineColonies is a snapshot build that moves
 * its internals regularly, so before Mixin tries to apply a MaricColonyTweaks mixin, ask
 * {@link MixinCompatibility} whether the MineColonies class and members that mixin needs
 * still exist. If not, skip the mixin - the feature falls back to vanilla MineColonies
 * behaviour and the player is told which feature went away, instead of the game dying at
 * boot with a mixin apply error.
 *
 * <p>Note on the signature: NeoForge 21.1.249 ships {@code sponge-mixin 0.15.2+mixin.0.8.7},
 * whose {@code IMixinConfigPlugin} only declares the two-argument
 * {@code shouldApplyMixin(String, String)}; the target-method-name overload arrived in a
 * later Mixin. The overload below is that entry point when it is available, and delegates
 * to the two-argument form otherwise, so the guard works on both.
 */
public class ColonyTweaksMixinPlugin implements IMixinConfigPlugin
{
    @Override
    public void onLoad(final String mixinPackage)
    {
        MixinCompatibility.logDetectedVersion();
    }

    @Override
    public String getRefMapperConfig()
    {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(final String mixinClassName, final String targetClassName)
    {
        return MixinCompatibility.shouldApply(simpleName(mixinClassName));
    }

    /**
     * Newer Mixin releases pass the target method name as well. MaricColonyTweaks does not need it -
     * {@link net.maric.colonytweaks.compat.MixinFeature} already knows every method, field and
     * constructor each mixin depends on, and checking those by descriptor catches a renamed
     * parameter type that a name-only check would miss.
     */
    public boolean shouldApplyMixin(final String mixinClassName, final String targetClassName, final String targetMethodName)
    {
        return shouldApplyMixin(mixinClassName, targetClassName);
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets)
    {
        // Nothing to filter: every MaricColonyTweaks mixin targets MineColonies, never Minecraft.
    }

    @Override
    public List<String> getMixins()
    {
        // null = use the mixin list from mariccolonytweaks.mixins.json unchanged.
        return null;
    }

    @Override
    public void preApply(
        final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo)
    {
        // No bytecode rewriting: MaricColonyTweaks only guards, it does not synthesise.
    }

    @Override
    public void postApply(
        final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo)
    {
        // Compatibility is decided in shouldApplyMixin, before anything is applied.
    }

    private static String simpleName(final String className)
    {
        final int lastDot = className.lastIndexOf('.');
        return lastDot < 0 ? className : className.substring(lastDot + 1);
    }
}
