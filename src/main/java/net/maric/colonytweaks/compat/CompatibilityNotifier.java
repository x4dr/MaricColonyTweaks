package net.maric.colonytweaks.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tells the player when a MaricColonyTweaks feature did not apply to this MineColonies build.
 *
 * <p>The message is a failure report, not a status line. A feature whose target class has not
 * loaded yet is not reported at all - it is not known to be broken, and there is no honest total
 * to report it out of. Only features Mixin actually reached and discarded are named, which is the
 * statement the player cares about: these are the ones that fall back to vanilla MineColonies.
 *
 * <p>A failure is discovered when the target class first loads, which for AI and GUI features can
 * be well into play, so the report cannot run only at login. It runs on login (for whatever is
 * already known) and once a second on the server, announcing a failure the first time it appears.
 * Server side, which also covers singleplayer: the integrated server and the client share a JVM,
 * so the client-only feature is part of the same report.
 *
 * <p>Built from literals rather than lang keys on purpose. This is the one message that must never
 * render as a raw translation key, because it is what tells the user why a feature is missing. The
 * wording lives in {@link FeatureReport}; this class only decides when to send it.
 */
public final class CompatibilityNotifier
{
    /** Failures already announced, so a failure is not repeated on every tick of a long session. */
    private static final Set<MixinFeature> ANNOUNCED = ConcurrentHashMap.newKeySet();

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

        final List<MixinFeature> failed = AppliedFeatures.failedFeatures();
        if (failed.isEmpty())
        {
            return;
        }

        ANNOUNCED.addAll(failed);
        report(player, failed);
    }

    /** Catches failures whose target class only loads during play, which login has already passed. */
    public static void onServerTick(final ServerTickEvent.Post event)
    {
        final MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0)
        {
            return;
        }

        final List<MixinFeature> failed = AppliedFeatures.failedFeatures();
        boolean fresh = false;
        for (final MixinFeature feature : failed)
        {
            if (ANNOUNCED.add(feature))
            {
                fresh = true;
            }
        }

        if (!fresh)
        {
            return;
        }

        AppliedFeatures.LOGGER.warn(FeatureReport.failureLogLine(failed));
        for (final ServerPlayer player : server.getPlayerList().getPlayers())
        {
            report(player, failed);
        }
    }

    /** One summary line for the log, written once at mod construction. */
    public static void logSummary()
    {
        final String summary = "MaricColonyTweaks: " + FeatureReport.logSummary();
        if (FeatureReport.summaryIsProblem())
        {
            AppliedFeatures.LOGGER.warn(summary);
        }
        else
        {
            AppliedFeatures.LOGGER.info(summary);
        }
    }

    private static void report(final ServerPlayer player, final List<MixinFeature> failed)
    {
        player.displayClientMessage(Component.literal(FeatureReport.chatText(failed)), false);
        player.displayClientMessage(Component.literal(FeatureReport.actionBarText(failed.size())), true);
    }
}
