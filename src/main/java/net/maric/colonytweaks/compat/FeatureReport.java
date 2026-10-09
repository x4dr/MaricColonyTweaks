package net.maric.colonytweaks.compat;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The wording of the feature report: one line for the log, three for chat, one for the action bar.
 *
 * <p>Deliberately free of Minecraft imports. {@link CompatibilityNotifier} only wraps these
 * strings in a {@code Component}, which keeps the text - the part that matters, and the part that
 * silently rots - testable without a game classpath.
 */
final class FeatureReport
{
    private static final String GOLD = "§e";
    private static final String RED = "§c";
    private static final String GREY = "§7";
    private static final String DARK = "§8";
    private static final String RESET = "§r";

    private FeatureReport()
    {
        // static utility
    }

    /** One summary line for the log, written once at mod construction. */
    static String logSummary()
    {
        final int total = AppliedFeatures.checkedCount();
        if (total == 0)
        {
            return "no mixin features were applied";
        }

        return AppliedFeatures.activeFeatures().size() + "/" + total
            + " features active; not loaded: " + names(AppliedFeatures.brokenFeatures());
    }

    /** Whether the log line is a problem worth warning about. */
    static boolean summaryIsProblem()
    {
        return AppliedFeatures.checkedCount() == 0
            || !AppliedFeatures.brokenFeatures().isEmpty();
    }

    /**
     * The chat report. Only called when something failed to load - a healthy mod says nothing,
     * so a message in chat always means something is wrong.
     *
     * <p>Spelled out rather than terse, because it is read rarely and only when it matters: the
     * count, the names, what the affected features do instead, and where the detail is.
     */
    static String chatText(final int active, final int total, final List<MixinFeature> broken)
    {
        final StringBuilder message = new StringBuilder();
        message.append(RED).append("[MaricColonyTweaks]").append(RESET).append(' ')
            .append(broken.size())
            .append(" of ")
            .append(total)
            .append(" features did NOT load on this MineColonies version:");
        for (final MixinFeature feature : broken)
        {
            message.append('\n').append(GREY).append(" - ").append(feature.featureName());
        }

        return message.append('\n')
            .append(GREY)
            .append(active)
            .append(" still active. The missing ones fall back to vanilla MineColonies behaviour.")
            .append('\n')
            .append(DARK)
            .append("See the log for the Mixin warnings naming the targets that moved.")
            .toString();
    }

    /** The one-line action bar, shown alongside the chat report and only for the same reason. */
    static String actionBarText(final int active, final int total)
    {
        return RED + "MaricColonyTweaks " + active + "/" + total + " features active" + DARK + " - see chat";
    }

    private static String names(final List<MixinFeature> features)
    {
        return features.isEmpty()
            ? "none"
            : features.stream().map(MixinFeature::featureName).collect(Collectors.joining(", "));
    }
}