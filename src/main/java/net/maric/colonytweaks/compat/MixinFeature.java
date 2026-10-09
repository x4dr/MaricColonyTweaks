package net.maric.colonytweaks.compat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The MaricColonyTweaks feature list: one entry per mixin in
 * {@code mariccolonytweaks.mixins.json}, with the name used in the log and the in-game report.
 *
 * <p>This is a naming table, not a compatibility check. Whether a feature actually works is
 * decided by Mixin at apply time and read back from
 * {@link AppliedFeatures}; a feature missing from that set is one Mixin discarded because its
 * injection target moved.
 *
 * <p>Adding a mixin means adding an entry here too: {@code AppliedFeatures} only sees mixins
 * Mixin actually applied, so an unlisted mixin could never be reported as broken.
 */
public enum MixinFeature
{
    /**
     * Support mixin: reads the inherited {@code AbstractAISkeleton.worker} field, which cannot
     * be {@code @Shadow}ed. Not a user-visible feature, so it has no entry - but
     * {@link #COURIER_CHEST_FULL_LOG} calls into it, so if it fails to generate that mixin has
     * to survive the missing accessor. See that feature's {@code LinkageError} guard.
     */
    BUILDER_UNSTICK(
        "BuilderUnstickMixin",
        "builder unstick"),

    COURIER_CHEST_FULL_LOG(
        "CourierFullChestLogMixin",
        "courier full-chest logging"),

    FARMER_BONEMEAL_SETTING(
        "FarmerBonemealSettingMixin",
        "per-hut Use Bone Meal setting"),

    FARMER_BONEMEAL_REQUEST(
        "FarmerBonemealRequestMixin",
        "bone meal-free fertilizer requests"),

    STUDENT_GRADUATION(
        "StudentGraduationMixin",
        "library student graduation"),

    UNIVERSITY_RESEARCH_REQUEST(
        "UniversityResearchRequestMixin",
        "university research item requests"),

    RESEARCH_REQUEST_CANCEL(
        "ResearchRequestCancelMixin",
        "research request cancel"),

    UNIVERSITY_RESEARCH_TICK(
        "BuildingUniversityResearchMixin",
        "university research auto-start"),

    RESEARCH_TREE_CLICK(
        "WindowResearchTreeRequestMixin",
        "clickable cost-missing research tiles");

    private static final Map<String, MixinFeature> BY_MIXIN = index();

    private final String mixinClassName;
    private final String featureName;

    MixinFeature(final String mixinClassName, final String featureName)
    {
        this.mixinClassName = mixinClassName;
        this.featureName = featureName;
    }

    private static Map<String, MixinFeature> index()
    {
        final Map<String, MixinFeature> map = new LinkedHashMap<>();
        for (final MixinFeature feature : values())
        {
            map.put(feature.mixinClassName, feature);
        }

        return Collections.unmodifiableMap(map);
    }

    /**
     * Resolves a mixin by its simple class name, which is how {@code postApply} refers to it.
     *
     * @return the feature, or {@code null} when the mixin is not in the table - a support
     * mixin such as the accessor, which has no user-visible behaviour to report.
     */
    public static MixinFeature byMixinClassName(final String simpleName)
    {
        return BY_MIXIN.get(simpleName);
    }

    public String mixinClassName()
    {
        return this.mixinClassName;
    }

    public String featureName()
    {
        return this.featureName;
    }

    /** The fully-qualified name of this feature's mixin class. */
    public String qualifiedMixinClassName()
    {
        return "net.maric.colonytweaks.mixin." + this.mixinClassName;
    }
}