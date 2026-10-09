package net.maric.colonytweaks.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the in-game report claims, given what Mixin did.
 *
 * <p>The two states are recorded by the plugin: {@code shouldApplyMixin} means Mixin intended to
 * apply a mixin, {@code postApply} means it was merged. A feature that was intended but never
 * merged is one Mixin threw away because an injection target had moved - that is the case the
 * player needs told about.
 *
 * <p>{@link AppliedFeatures} keeps its state in static sets, so each test drives it with its own
 * mixin and asserts on that one feature rather than on whole-list contents.
 */
@DisplayName("The report distinguishes features Mixin applied from features it discarded")
class AppliedFeaturesTest
{
    @Test
    @DisplayName("a mixin that reached postApply is reported as working")
    void appliedMixinIsActive()
    {
        attemptAndApply(MixinFeature.BUILDER_UNSTICK);

        assertTrue(AppliedFeatures.activeFeatures().contains(MixinFeature.BUILDER_UNSTICK),
            "postApply fired, so this feature loaded and should be reported as active");
        assertFalse(AppliedFeatures.brokenFeatures().contains(MixinFeature.BUILDER_UNSTICK));
    }

    @Test
    @DisplayName("a mixin that was attempted but never applied is reported as not loading")
    void attemptedButNotAppliedIsBroken()
    {
        AppliedFeatures.recordAttempt(MixinFeature.FARMER_BONEMEAL_REQUEST.qualifiedMixinClassName());

        assertTrue(AppliedFeatures.brokenFeatures().contains(MixinFeature.FARMER_BONEMEAL_REQUEST),
            "shouldApplyMixin fired but postApply did not, so this feature did not load and the "
                + "player has to be told");
        assertFalse(AppliedFeatures.activeFeatures().contains(MixinFeature.FARMER_BONEMEAL_REQUEST));
    }

    @Test
    @DisplayName("a feature Mixin never asked about is in neither list")
    void untouchedFeatureIsNotReported()
    {
        assertFalse(AppliedFeatures.activeFeatures().contains(MixinFeature.RESEARCH_TREE_CLICK));
        assertFalse(AppliedFeatures.brokenFeatures().contains(MixinFeature.RESEARCH_TREE_CLICK),
            "on a dedicated server the client mixin is never applied, so calling it broken would "
                + "be a lie");
    }

    @Test
    @DisplayName("the two lists never overlap, and together cover exactly what was checked")
    void activeAndBrokenPartitionWhatWasChecked()
    {
        attemptAndApply(MixinFeature.STUDENT_GRADUATION);
        attemptAndApply(MixinFeature.UNIVERSITY_RESEARCH_TICK);
        AppliedFeatures.recordAttempt(MixinFeature.RESEARCH_TREE_CLICK.qualifiedMixinClassName());

        final List<MixinFeature> active = AppliedFeatures.activeFeatures();
        final List<MixinFeature> broken = AppliedFeatures.brokenFeatures();

        for (final MixinFeature feature : active)
        {
            assertFalse(broken.contains(feature), feature.featureName() + " is both active and broken");
        }

        for (final MixinFeature feature : broken)
        {
            assertFalse(active.contains(feature), feature.featureName() + " is both active and broken");
        }

        assertEquals(AppliedFeatures.checkedCount(), active.size() + broken.size(),
            "every attempted feature belongs to exactly one of the two lists");
        assertTrue(active.containsAll(List.of(MixinFeature.STUDENT_GRADUATION, MixinFeature.UNIVERSITY_RESEARCH_TICK)));
        assertTrue(broken.contains(MixinFeature.RESEARCH_TREE_CLICK));
    }

    @Test
    @DisplayName("the chat report names every feature that did not load")
    void chatReportNamesBrokenFeatures()
    {
        final List<MixinFeature> broken = List.of(MixinFeature.FARMER_BONEMEAL_SETTING, MixinFeature.STUDENT_GRADUATION);
        final String text = FeatureReport.chatText(7, 9, broken);

        for (final MixinFeature feature : broken)
        {
            assertTrue(text.contains(feature.featureName()),
                "the report must name \"" + feature.featureName() + "\" or the player cannot tell "
                    + "what is missing");
        }
    }

    @Test
    @DisplayName("the chat report says how many are left and that the rest go back to vanilla")
    void chatReportExplainsTheFallback()
    {
        final String text = FeatureReport.chatText(7, 9, List.of(MixinFeature.STUDENT_GRADUATION));

        assertTrue(text.contains("7"), "the report says how many still work");
        assertTrue(text.contains("vanilla MineColonies behaviour"),
            "a feature that quietly stops is worse than one that is explained");
    }

    @Test
    @DisplayName("the action bar states the same counts in one line")
    void actionBarStatesCounts()
    {
        final String text = FeatureReport.actionBarText(7, 9);

        assertTrue(text.contains("7/9"), "the bar carries the count without opening chat");
    }

    @Test
    @DisplayName("a mixin with no feature entry is ignored rather than counted")
    void unknownMixinIsIgnored()
    {
        final int before = AppliedFeatures.checkedCount();
        AppliedFeatures.recordAttempt("net.maric.colonytweaks.mixin.SomeMixinNobodyDeclared");
        AppliedFeatures.recordApplied("net.maric.colonytweaks.mixin.SomeMixinNobodyDeclared");

        assertEquals(before, AppliedFeatures.checkedCount(),
            "a mixin outside the table has no name to report, so it must not change the totals");
    }

    private static void attemptAndApply(final MixinFeature feature)
    {
        AppliedFeatures.recordAttempt(feature.qualifiedMixinClassName());
        AppliedFeatures.recordApplied(feature.qualifiedMixinClassName());
    }
}