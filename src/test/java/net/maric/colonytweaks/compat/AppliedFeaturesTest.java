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
 * <p>The three states are recorded by the plugin: {@code shouldApplyMixin} means Mixin intended to
 * apply a mixin, {@code preApply} means the target class had loaded and application started,
 * {@code postApply} means it merged. Attempted-but-not-pre-applied is only <em>pending</em> - the
 * class has not loaded, so nothing is known. Pre-applied-but-not-applied is <em>failed</em> - the
 * class loaded and the mixin was dropped, which is the incompatibility this mod reports.
 *
 * <p>{@link AppliedFeatures} keeps its state in static sets, so each test drives it with its own
 * mixin and asserts on that one feature rather than on whole-list contents.
 */
@DisplayName("The report tells verified, failed and pending apart")
class AppliedFeaturesTest
{
    @Test
    @DisplayName("a mixin that reached postApply is reported as working")
    void appliedMixinIsActive()
    {
        attemptPreApplyAndApply(MixinFeature.BUILDER_UNSTICK);

        assertTrue(AppliedFeatures.activeFeatures().contains(MixinFeature.BUILDER_UNSTICK),
            "postApply fired, so this feature loaded and should be reported as active");
        assertFalse(AppliedFeatures.pendingFeatures().contains(MixinFeature.BUILDER_UNSTICK));
        assertFalse(AppliedFeatures.failedFeatures().contains(MixinFeature.BUILDER_UNSTICK));
    }

    @Test
    @DisplayName("a mixin whose target loaded but that failed to apply is reported as failed")
    void preAppliedButNotAppliedIsFailed()
    {
        attemptAndPreApply(MixinFeature.COURIER_CHEST_FULL_LOG);

        assertTrue(AppliedFeatures.failedFeatures().contains(MixinFeature.COURIER_CHEST_FULL_LOG),
            "preApply fired but postApply did not, so the target loaded and the injection failed; "
                + "this is a compatibility failure and must be reported as one");
        assertFalse(AppliedFeatures.activeFeatures().contains(MixinFeature.COURIER_CHEST_FULL_LOG));
        assertFalse(AppliedFeatures.pendingFeatures().contains(MixinFeature.COURIER_CHEST_FULL_LOG),
            "the target class loaded, so calling this pending would understate a real failure");
    }

    @Test
    @DisplayName("a mixin that was attempted but whose class never loaded is reported as pending")
    void attemptedButNotAppliedIsPending()
    {
        AppliedFeatures.recordAttempt(MixinFeature.FARMER_BONEMEAL_REQUEST.qualifiedMixinClassName());

        assertTrue(AppliedFeatures.pendingFeatures().contains(MixinFeature.FARMER_BONEMEAL_REQUEST),
            "shouldApplyMixin fired but preApply did not, so the target class has not loaded yet; "
                + "the report may only call this pending");
        assertFalse(AppliedFeatures.activeFeatures().contains(MixinFeature.FARMER_BONEMEAL_REQUEST));
        assertFalse(AppliedFeatures.failedFeatures().contains(MixinFeature.FARMER_BONEMEAL_REQUEST));
    }

    @Test
    @DisplayName("a feature Mixin never asked about is in none of the lists")
    void untouchedFeatureIsNotReported()
    {
        assertFalse(AppliedFeatures.activeFeatures().contains(MixinFeature.RESEARCH_TREE_CLICK));
        assertFalse(AppliedFeatures.pendingFeatures().contains(MixinFeature.RESEARCH_TREE_CLICK),
            "on a dedicated server the client mixin is never attempted, so calling it pending "
                + "would be a lie");
        assertFalse(AppliedFeatures.failedFeatures().contains(MixinFeature.RESEARCH_TREE_CLICK));
    }

    @Test
    @DisplayName("the three lists never overlap, and together cover exactly what was checked")
    void theThreeListsPartitionWhatWasChecked()
    {
        attemptPreApplyAndApply(MixinFeature.STUDENT_GRADUATION);
        attemptPreApplyAndApply(MixinFeature.UNIVERSITY_RESEARCH_TICK);
        attemptAndPreApply(MixinFeature.RESEARCH_REQUEST_CANCEL);
        AppliedFeatures.recordAttempt(MixinFeature.FARMER_BONEMEAL_SETTING.qualifiedMixinClassName());

        final List<MixinFeature> active = AppliedFeatures.activeFeatures();
        final List<MixinFeature> failed = AppliedFeatures.failedFeatures();
        final List<MixinFeature> pending = AppliedFeatures.pendingFeatures();

        for (final MixinFeature feature : MixinFeature.values())
        {
            final int memberships = (active.contains(feature) ? 1 : 0)
                + (failed.contains(feature) ? 1 : 0)
                + (pending.contains(feature) ? 1 : 0);
            assertTrue(memberships <= 1, feature.featureName() + " is in more than one list");
        }

        assertEquals(AppliedFeatures.checkedCount(), active.size() + failed.size() + pending.size(),
            "every attempted feature belongs to exactly one of the three lists");
        assertTrue(active.containsAll(List.of(MixinFeature.STUDENT_GRADUATION, MixinFeature.UNIVERSITY_RESEARCH_TICK)));
        assertTrue(failed.contains(MixinFeature.RESEARCH_REQUEST_CANCEL));
        assertTrue(pending.contains(MixinFeature.FARMER_BONEMEAL_SETTING));
    }

    @Test
    @DisplayName("the chat report names every failed feature")
    void chatReportNamesFailedFeatures()
    {
        final List<MixinFeature> failed = List.of(MixinFeature.COURIER_CHEST_FULL_LOG, MixinFeature.RESEARCH_REQUEST_CANCEL);
        final String text = FeatureReport.chatText(failed);

        for (final MixinFeature feature : failed)
        {
            assertTrue(text.contains(feature.featureName()),
                "the report must name \"" + feature.featureName() + "\" or the player cannot tell "
                    + "which feature is broken");
        }
    }

    @Test
    @DisplayName("the chat report is failure-only and carries no 'N of M' total")
    void chatReportHasNoTotal()
    {
        final String text = FeatureReport.chatText(List.of(MixinFeature.COURIER_CHEST_FULL_LOG));

        assertTrue(text.contains("failed to apply"),
            "the whole message is a failure report - it only exists when something failed");
        assertFalse(text.contains(" of "),
            "there is no honest total when this fires: not 9, and not a loaded-so-far subset whose "
                + "size depends on what classes happen to have loaded");
        assertFalse(text.toLowerCase().contains("pending"),
            "a feature whose class has not loaded is not part of this message");
    }

    @Test
    @DisplayName("the action bar states the failure count in one line")
    void actionBarStatesFailureCount()
    {
        final String text = FeatureReport.actionBarText(2);

        assertTrue(text.contains("2"), "the bar carries the count without opening chat");
        assertTrue(text.toLowerCase().contains("failed"), "the bar is a failure notice");
    }

    @Test
    @DisplayName("a mixin with no feature entry is ignored rather than counted")
    void unknownMixinIsIgnored()
    {
        final int before = AppliedFeatures.checkedCount();
        AppliedFeatures.recordAttempt("net.maric.colonytweaks.mixin.SomeMixinNobodyDeclared");
        AppliedFeatures.recordPreApply("net.maric.colonytweaks.mixin.SomeMixinNobodyDeclared");
        AppliedFeatures.recordApplied("net.maric.colonytweaks.mixin.SomeMixinNobodyDeclared");

        assertEquals(before, AppliedFeatures.checkedCount(),
            "a mixin outside the table has no name to report, so it must not change the totals");
    }

    private static void attemptPreApplyAndApply(final MixinFeature feature)
    {
        AppliedFeatures.recordAttempt(feature.qualifiedMixinClassName());
        AppliedFeatures.recordPreApply(feature.qualifiedMixinClassName());
        AppliedFeatures.recordApplied(feature.qualifiedMixinClassName());
    }

    private static void attemptAndPreApply(final MixinFeature feature)
    {
        AppliedFeatures.recordAttempt(feature.qualifiedMixinClassName());
        AppliedFeatures.recordPreApply(feature.qualifiedMixinClassName());
    }
}
