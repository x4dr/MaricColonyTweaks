package net.maric.colonytweaks.mixin;

import net.maric.colonytweaks.compat.AppliedFeatures;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin config plugin for {@code mariccolonytweaks.mixins.json}.
 *
 * <p>It applies every mixin. Compatibility is not decided here: Mixin itself drops a mixin whose
 * injection target no longer exists, because {@code mariccolonytweaks.mixins.json} sets
 * {@code injectors.defaultRequire = 1}, and the same config's {@code "required": false} keeps
 * that failure from taking the game down. This plugin's only job is to note which mixins
 * survived, so {@link AppliedFeatures} can tell the player which features are not working.
 *
 * <p>Note on the signature: NeoForge 21.1.249 ships {@code sponge-mixin 0.15.2+mixin.0.8.7},
 * whose {@code IMixinConfigPlugin} only declares the two-argument
 * {@code shouldApplyMixin(String, String)}; the target-method-name overload arrived in a
 * later Mixin. The overload below is that entry point when it is available, and delegates
 * to the two-argument form otherwise, so the reporting works on both.
 */
public class ColonyTweaksMixinPlugin implements IMixinConfigPlugin
{
    @Override
    public void onLoad(final String mixinPackage)
    {
        // Nothing to prepare. The feature state is filled in by shouldApplyMixin/postApply.
    }

    @Override
    public String getRefMapperConfig()
    {
        return null;
    }

    /**
     * Mixin calls this as {@code shouldApplyMixin(targetClassName, mixinClassName)} - the
     * <em>target</em> comes first. See {@code PluginHandle.shouldApplyMixin} and
     * {@code MixinInfo.shouldApplyMixin}, which both pass {@code (targetName, this.className)}.
     */
    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName)
    {
        AppliedFeatures.recordAttempt(mixinClassName);
        return true;
    }

    /**
     * Newer Mixin releases pass the target method name as well. MaricColonyTweaks does not need
     * it - Mixin has already matched the injection point by the time it gets here.
     */
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName, final String targetMethodName)
    {
        return shouldApplyMixin(targetClassName, mixinClassName);
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
        // Reached once the target class has loaded, before any injection runs. Reaching this and
        // never postApply is how a moved injection target is told apart from a class that has not
        // loaded yet.
        AppliedFeatures.recordPreApply(mixinClassName);
    }

    @Override
    public void postApply(
        final String targetClassName, final ClassNode targetClass, final String mixinClassName, final IMixinInfo mixinInfo)
    {
        // Reached only if every injection in this mixin was applied - a target that moved
        // throws and Mixin discards the mixin before this point.
        AppliedFeatures.recordApplied(mixinClassName);
    }
}