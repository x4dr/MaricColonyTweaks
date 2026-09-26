package net.maric.colonytweaks.compat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;

/**
 * Tells the player, in game, which MaricColonyTweaks features this MineColonies build supports.
 *
 * <p>Failures in the log are easy to miss; on join they are impossible to miss. Fires once
 * per login, on the server side, which also covers singleplayer: the integrated server and
 * the client share a JVM, so the client-only features are part of the same report.
 *
 * <p>Built from literals rather than lang keys on purpose. This is the one message that
 * must never render as a raw translation key, because it is what tells the user why a
 * feature is missing.
 */
public final class CompatibilityNotifier
{
    private static final String GOLD = "§e";
    private static final String RED = "§c";
    private static final String GREY = "§7";
    private static final String DARK = "§8";
    private static final String RESET = "§r";

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

        final List<MixinFeature> active = MixinCompatibility.activeFeatures();
        final List<MixinFeature> disabled = MixinCompatibility.disabledFeatures();
        final int total = active.size() + disabled.size();
        if (total == 0)
        {
            return;
        }

        player.displayClientMessage(chatMessage(active.size(), total, MixinCompatibility.disabledReport()), false);
        player.displayClientMessage(actionBar(active.size(), total), true);
    }

    /** The multi-line chat report. Visible to tests and to anyone printing it. */
    static Component chatMessage(final int active, final int total, final List<String> disabled)
    {
        if (disabled.isEmpty())
        {
            return Component.literal(GOLD + "[MaricColonyTweaks]" + RESET + " Active - all " + total
                + " features enabled (MineColonies " + MixinCompatibility.minecoloniesVersion() + ").");
        }

        final StringBuilder message = new StringBuilder();
        message.append(RED).append("[MaricColonyTweaks]").append(RESET).append(' ')
            .append(disabled.size())
            .append(" of ")
            .append(total)
            .append(" features DISABLED on MineColonies ")
            .append(MixinCompatibility.minecoloniesVersion())
            .append(':');
        for (final String line : disabled)
        {
            message.append('\n').append(GREY).append(" - ").append(line);
        }

        return Component.literal(message.append('\n')
            .append(GREY)
            .append(active)
            .append(" still active. The disabled ones fall back to vanilla MineColonies behaviour - this MineColonies version does not support them.")
            .append('\n')
            .append(DARK)
            .append("See the log for the full target names.")
            .toString());
    }

    /** The one-line action bar, so the state is visible even when chat is not. */
    static Component actionBar(final int active, final int total)
    {
        return total - active == 0
            ? Component.literal(GOLD + "MaricColonyTweaks" + RESET + " " + active + "/" + total + " features active")
            : Component.literal(RED + "MaricColonyTweaks " + active + "/" + total + " features active" + DARK + " - see chat");
    }
}
