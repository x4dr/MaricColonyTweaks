package net.maric.colonytweaks.compat;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which MaricColonyTweaks mixins Mixin actually applied.
 *
 * <p>The compatibility question - "does this MineColonies build still have the method this
 * mixin injects into?" - is answered by Mixin itself, not by this mod.
 * {@code mariccolonytweaks.mixins.json} sets {@code injectors.defaultRequire = 1}, so an
 * injector whose target moved throws {@code InvalidInjectionException} instead of silently
 * no-op'ing, Mixin discards the whole mixin, and {@code postApply} never fires for it. The
 * config's own {@code "required": false} is what keeps that from taking the game down: Mixin
 * downgrades the failure to a warning and carries on booting.
 *
 * <p>So a feature that was attempted but never applied is one that does not work, and the
 * difference between the two sets is exactly the set to tell the player about. Nothing here
 * inspects MineColonies bytecode, which is the point: an earlier version pre-checked every
 * target member and had to work around Mixin refusing any mixin whose target got loaded during
 * config prepare. Letting Mixin fail the injection removes the need for that workaround.
 *
 * <p>Written from Mixin's transformer thread and read from the server thread, so both sets are
 * concurrent.
 */
public final class AppliedFeatures
{
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final Set<MixinFeature> ATTEMPTED = ConcurrentHashMap.newKeySet();
    private static final Set<MixinFeature> APPLIED = ConcurrentHashMap.newKeySet();

    private AppliedFeatures()
    {
        // static utility
    }

    /** Called from {@code shouldApplyMixin}: Mixin intends to apply this mixin. */
    public static void recordAttempt(final String mixinClassName)
    {
        final MixinFeature feature = MixinFeature.byMixinClassName(simpleName(mixinClassName));
        if (feature == null)
        {
            LOGGER.debug("MaricColonyTweaks: {} has no feature entry; not reported.", mixinClassName);
            return;
        }

        ATTEMPTED.add(feature);
    }

    /** Called from {@code postApply}: this mixin was merged into its target. */
    public static void recordApplied(final String mixinClassName)
    {
        final MixinFeature feature = MixinFeature.byMixinClassName(simpleName(mixinClassName));
        if (feature != null)
        {
            APPLIED.add(feature);
        }
    }

    /**
     * Features that work. Only features Mixin actually attempted are counted - on a dedicated
     * server the client mixin is never applied, and reporting it as active would be a lie.
     */
    public static List<MixinFeature> activeFeatures()
    {
        return list(APPLIED);
    }

    /**
     * Features that do not work: Mixin tried to apply the mixin and discarded it, because an
     * injection target had moved. Each falls back to vanilla MineColonies behaviour.
     */
    public static List<MixinFeature> brokenFeatures()
    {
        final EnumSet<MixinFeature> broken = EnumSet.noneOf(MixinFeature.class);
        for (final MixinFeature feature : ATTEMPTED)
        {
            if (!APPLIED.contains(feature))
            {
                broken.add(feature);
            }
        }

        return list(broken);
    }

    /** How many features to divide into working and broken. Zero before Mixin has run. */
    public static int checkedCount()
    {
        return ATTEMPTED.size();
    }

    private static List<MixinFeature> list(final Set<MixinFeature> features)
    {
        // Not EnumSet.copyOf: that throws on an empty non-EnumSet collection, and "nothing
        // applied yet" is a normal state before Mixin has run.
        final EnumSet<MixinFeature> ordered = EnumSet.noneOf(MixinFeature.class);
        ordered.addAll(features);
        return List.copyOf(ordered);
    }

    private static String simpleName(final String className)
    {
        final int lastDot = className.lastIndexOf('.');
        return lastDot < 0 ? className : className.substring(lastDot + 1);
    }
}