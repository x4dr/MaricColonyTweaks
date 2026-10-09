package net.maric.colonytweaks.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;

/**
 * Tells the player, in game, which MaricColonyTweaks features this MineColonies build supports.
 *
 * <p>A feature that did not load is otherwise invisible: Mixin discards a mixin whose injection
 * target moved, logs a warning, and the game boots as if nothing were wrong. On join that is
 * impossible to miss. Fires once per login, on the server side, which also covers singleplayer:
 * the integrated server and the client share a JVM, so the client-only feature is part of the
 * same report.
 *
 * <p>Built from literals rather than lang keys on purpose. This is the one message that must
 * never render as a raw translation key, because it is what tells the user why a feature is
 * missing. The wording lives in {@link FeatureReport}; this class only wires it to the event and
 * wraps it in a {@link Component}.
 */
public final class CompatibilityNotifier
{
    private CompatibilityNotifier()
    {
        // static utility
    }

    public static void onPlayerLoggedIn(final PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof final ServerPlayer player))
        {
            return;
        }

        // Silent when everything loaded: a message in chat has to mean something is wrong,
        // otherwise it is noise on every single join.
        final List<MixinFeature> broken = AppliedFeatures.brokenFeatures();
        if (broken.isEmpty())
        {
            return;
        }

        final int total = AppliedFeatures.checkedCount();
        final int active = AppliedFeatures.activeFeatures().size();
        player.displayClientMessage(Component.literal(FeatureReport.chatText(active, total, broken)), false);
        player.displayClientMessage(Component.literal(FeatureReport.actionBarText(active, total)), true);
    }

    /** One summary line for the log. Called once at mod construction. */
    public static void logSummary()
    {
        final String summary = "MaricColonyTweaks: " + FeatureReport.logSummary();
        if (FeatureReport.summaryIsProblem())
        {
            AppliedFeatures.LOGGER.warn(summary
                + " - an injection target moved in this MineColonies build; see the Mixin warnings above.");
        }
        else
        {
            AppliedFeatures.LOGGER.info(summary);
        }
    }
}