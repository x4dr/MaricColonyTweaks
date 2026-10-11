package net.maric.colonytweaks.compat;

import java.util.List;
import java.util.StringJoiner;

/**
 * The wording of the feature report.
 *
 * <p>The player-facing message is a failure report and nothing else: it names the features whose
 * target class loaded and whose mixin then failed to apply, because that is the only statement
 * about compatibility the mod can actually make. A feature whose class has not loaded yet is not
 * mentioned - saying anything about it would be a guess, and "N of 9" would be wrong on its face,
 * since the total is both time-dependent (classes load lazily) and side-dependent (the client-only
 * mixin never counts on a dedicated server).
 *
 * <p>Deliberately free of Minecraft imports. {@link CompatibilityNotifier} only wraps these
 * strings in a {@code Component}, which keeps the text - the part that matters, and the part that
 * silently rots - testable without a game classpath.
 */
final class FeatureReport
{
    private static final String GREY = "§7";
    private static final String DARK = "§8";
    private static final String RED = "§c";
    private static final String RESET = "§r";

    private FeatureReport()
    {
        // static utility
    }

    /**
     * One summary line for the log, written once at mod construction.
     *
     * <p>Written before most MineColonies classes have loaded, so at that moment almost every
     * feature is still pending and none can have failed. Both counts grow during play; this line
     * is a dev-facing snapshot, not the player-facing verdict.
     */
    static String logSummary()
    {
        final int total = AppliedFeatures.checkedCount();
        if (total == 0)
        {
            return "no mixin features were checked";
        }

        final int active = AppliedFeatures.activeFeatures().size();
        final int pending = AppliedFeatures.pendingFeatures().size();
        final List<MixinFeature> failed = AppliedFeatures.failedFeatures();

        final StringBuilder summary = new StringBuilder();
        summary.append(active).append('/').append(total).append(" features verified");
        if (!failed.isEmpty())
        {
            summary.append("; failed to apply on this MineColonies build: ").append(names(failed));
        }

        return summary.append("; ").append(pending)
            .append(" pending until their MineColonies class first loads")
            .toString();
    }

    /**
     * Whether the log line is a problem worth warning about. A failed feature is. Pending alone is
     * normal and never a warning.
     */
    static boolean summaryIsProblem()
    {
        return AppliedFeatures.checkedCount() == 0 || !AppliedFeatures.failedFeatures().isEmpty();
    }

    /** One log line per batch of newly discovered failures, naming every feature failed so far. */
    static String failureLogLine(final List<MixinFeature> failed)
    {
        return "MaricColonyTweaks: " + failed.size()
            + (failed.size() == 1
                ? " feature failed to apply on this MineColonies build: "
                : " features failed to apply on this MineColonies build: ")
            + names(failed);
    }

    /**
     * The player-facing report. Only ever built when at least one feature has failed, so it has no
     * "N of M" header and mentions nothing still pending: a failure is the whole message.
     */
    static String chatText(final List<MixinFeature> failed)
    {
        final StringBuilder message = new StringBuilder();
        message.append(RED).append("[MaricColonyTweaks]").append(RESET).append(' ')
            .append(failed.size())
            .append(failed.size() == 1 ? " feature failed to apply" : " features failed to apply")
            .append(" on this MineColonies version:");

        for (final MixinFeature feature : failed)
        {
            message.append('\n').append(GREY).append(" - ").append(feature.featureName());
        }

        return message.append('\n').append(GREY)
            .append("They fall back to vanilla MineColonies behaviour; other features keep")
            .append(" working. ").append(DARK)
            .append("See the log for the Mixin warnings naming the injection targets that moved.")
            .toString();
    }

    /** The one-line action bar, shown alongside the chat report and only for the same reason. */
    static String actionBarText(final int failed)
    {
        return RED + "MaricColonyTweaks: " + failed
            + (failed == 1 ? " feature failed" : " features failed") + DARK + " - see chat";
    }

    private static String names(final List<MixinFeature> features)
    {
        if (features.isEmpty())
        {
            return "none";
        }

        final StringJoiner joiner = new StringJoiner(", ");
        for (final MixinFeature feature : features)
        {
            joiner.add(feature.featureName());
        }

        return joiner.toString();
    }
}
