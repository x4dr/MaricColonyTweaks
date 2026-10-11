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
 * <p>What this class tracks is the difference between the three callbacks, which together
 * separate "not visited yet" from "genuinely broke":
 *
 * <ul>
 *   <li>{@code shouldApplyMixin} fires once, at config prepare, for every mixin in the config,
 *       whether or not its target class ever loads - that is {@link #ATTEMPTED}.</li>
 *   <li>{@code preApply} fires when the target class <em>has</em> loaded and Mixin is about to
 *       merge the mixin into it - that is {@link #PRE_APPLIED}.</li>
 *   <li>{@code postApply} fires only if every injection merged - that is {@link #APPLIED}.</li>
 * </ul>
 *
 * <p>So a feature that was attempted but never reached {@code preApply} has a target class that
 * has not loaded yet - it is pending, and nothing is known about its compatibility. A feature
 * that reached {@code preApply} but never {@code postApply} loaded its target and failed to
 * apply: that is the compatibility failure this mod exists to report, and Mixin has already
 * logged the injection target it wanted.
 *
 * <p>Written from Mixin's transformer thread and read from the server thread, so every set is
 * concurrent.
 */
public final class AppliedFeatures
{
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final Set<MixinFeature> ATTEMPTED = ConcurrentHashMap.newKeySet();
    private static final Set<MixinFeature> PRE_APPLIED = ConcurrentHashMap.newKeySet();
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

    /**
     * Called from {@code preApply}: the target class has loaded and Mixin is applying this mixin
     * to it. Reaching here and never reaching {@link #recordApplied} is a failure, not a pending
     * feature.
     */
    public static void recordPreApply(final String mixinClassName)
    {
        final MixinFeature feature = MixinFeature.byMixinClassName(simpleName(mixinClassName));
        if (feature != null)
        {
            PRE_APPLIED.add(feature);
        }
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
     * Features whose target class has not loaded yet.
     *
     * <p>Mixin merges a mixin the first time its target class is loaded, and MineColonies loads
     * many classes lazily - an AI worker's class loads when that citizen first ticks, a GUI class
     * when the screen opens. So between mod construction and that first load, a feature is
     * neither active nor broken: it is unverified. Claiming anything about it would be a guess.
     *
     * <p>The set shrinks as {@code preApply} fires; it is not an error report.
     */
    public static List<MixinFeature> pendingFeatures()
    {
        final EnumSet<MixinFeature> pending = EnumSet.noneOf(MixinFeature.class);
        for (final MixinFeature feature : ATTEMPTED)
        {
            if (!PRE_APPLIED.contains(feature))
            {
                pending.add(feature);
            }
        }

        return list(pending);
    }

    /**
     * Features whose target class loaded and whose mixin then failed to apply.
     *
     * <p>{@code preApply} fired, so Mixin reached the target class; {@code postApply} did not, so
     * an injection threw and Mixin discarded the mixin whole (the config is {@code required:
     * false}, which is why the game kept booting). This is the compatibility failure: the feature
     * does not work on this MineColonies build, and Mixin's own warning names the target it
     * wanted.
     */
    public static List<MixinFeature> failedFeatures()
    {
        final EnumSet<MixinFeature> failed = EnumSet.noneOf(MixinFeature.class);
        for (final MixinFeature feature : PRE_APPLIED)
        {
            if (!APPLIED.contains(feature))
            {
                failed.add(feature);
            }
        }

        return list(failed);
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