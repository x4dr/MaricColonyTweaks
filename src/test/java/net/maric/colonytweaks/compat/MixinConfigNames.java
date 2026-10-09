package net.maric.colonytweaks.compat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the mixin list straight out of {@code mariccolonytweaks.mixins.json}.
 *
 * <p>The compatibility table and the mixin config have to agree. If a mixin is in the config with
 * no table entry it is applied completely unguarded - the guard logs a warning and returns true -
 * and if the table names a mixin the config does not list it can never be applied. Either way the
 * drift is silent, so the tests read the real config rather than a hand-kept copy of it.
 *
 * <p>Deliberately not a JSON parser: this is a test helper and the mod's own guard is careful not
 * to take on dependencies, so it would be odd for the tests to need one. The mixin list entries
 * are plain strings, which is all that has to be read.
 */
final class MixinConfigNames
{
    private static final String CONFIG = "mariccolonytweaks.mixins.json";

    private MixinConfigNames()
    {
        // static utility
    }

    /** Mixin class simple names, in config order, from the {@code mixins} array. */
    static List<String> mixinsInConfigOrder()
    {
        return array("mixins");
    }

    /** Mixin class simple names from the {@code client} array. */
    static List<String> clientMixins()
    {
        return array("client");
    }

    /** Whether the config is {@code required}, which decides whether a failure is fatal. */
    static boolean isRequired()
    {
        return !json().contains("\"required\" : false") && !json().contains("\"required\": false");
    }

    /**
     * {@code injectors.defaultRequire} from the config.
     *
     * <p>This one number is what makes a broken feature visible: at 0 an injection whose target
     * moved is silently skipped and the mixin still reports as applied, so there is nothing to
     * tell the player. At 1 Mixin throws, discards the mixin, and the missing feature shows up
     * as missing.
     */
    static int defaultRequire()
    {
        final String json = json();
        final int key = json.indexOf("\"defaultRequire\"");
        if (key < 0)
        {
            throw new IllegalStateException(CONFIG + " does not set injectors.defaultRequire");
        }

        int i = key;
        while (i < json.length() && !Character.isDigit(json.charAt(i)))
        {
            i++;
        }

        int end = i;
        while (end < json.length() && Character.isDigit(json.charAt(end)))
        {
            end++;
        }

        if (i == end)
        {
            throw new IllegalStateException(CONFIG + " has a non-numeric injectors.defaultRequire");
        }

        return Integer.parseInt(json.substring(i, end));
    }

    private static List<String> array(final String key)
    {
        final String json = json();
        final int start = json.indexOf('"' + key + '"');
        if (start < 0)
        {
            return List.of();
        }

        final int open = json.indexOf('[', start);
        final int close = json.indexOf(']', open);
        if (open < 0 || close < 0)
        {
            return List.of();
        }

        final List<String> names = new ArrayList<>();
        for (String entry : json.substring(open + 1, close).split(","))
        {
            entry = entry.trim();
            if (entry.startsWith("\"") && entry.endsWith("\"") && entry.length() > 2)
            {
                names.add(entry.substring(1, entry.length() - 1));
            }
        }

        return names;
    }

    private static String json()
    {
        try (InputStream in = MixinConfigNames.class.getClassLoader().getResourceAsStream(CONFIG))
        {
            if (in == null)
            {
                throw new IllegalStateException(CONFIG + " is not on the test classpath");
            }

            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (final IOException e)
        {
            throw new UncheckedIOException("could not read " + CONFIG, e);
        }
    }
}