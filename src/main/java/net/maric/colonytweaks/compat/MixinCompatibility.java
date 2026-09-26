package net.maric.colonytweaks.compat;

import com.mojang.logging.LogUtils;
import net.maric.colonytweaks.compat.MixinFeature.Member;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The MaricColonyTweaks mixin compatibility guard.
 *
 * <p>MineColonies is a moving target (the user runs a snapshot build), so every mixin in
 * {@link MixinFeature} declares the MineColonies members it depends on. This class checks
 * them reflectively while Mixin is still preparing its config, and answers a single
 * question: may this mixin be applied, or must it be skipped so the game keeps running
 * with vanilla MineColonies behaviour?
 *
 * <p>Fail-open by design: a feature is only disabled when its target is <em>proven</em>
 * missing. If the check itself cannot run (class not loadable yet, access denied, ...) the
 * mixin is left enabled, and {@code "required": false} plus the {@code require = 0}
 * injectors are the backstop - so a bug in the guard can never be the thing that breaks a
 * working setup.
 */
public final class MixinCompatibility
{
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final String MINECOLONIES_MOD_ID = "minecolonies";
    private static final String UNKNOWN_VERSION = "unknown";

    private static final Pattern MOD_ID = Pattern.compile("^modId\\s*=\\s*\"([^\"]+)\"$");
    private static final Pattern VERSION = Pattern.compile("^version\\s*=\\s*\"([^\"]+)\"$");

    /** Features Mixin asked about, whether or not they turned out to be usable. */
    private static final Set<MixinFeature> CHECKED = ConcurrentHashMap.newKeySet();

    /** Features proven incompatible, mapped to why. */
    private static final Map<MixinFeature, Failure> DISABLED = new ConcurrentHashMap<>();

    /**
     * Why a feature is off. The log wants the fully qualified {@link #detailed()}, the
     * chat wants the {@link #shortLabel()}.
     */
    private record Failure(String detailed, String shortLabel)
    {
    }

    private static volatile String minecoloniesVersion;
    private static volatile boolean versionLogged;

    private MixinCompatibility()
    {
        // static utility
    }

    /**
     * The entry point used by {@code ColonyTweaksMixinPlugin#shouldApplyMixin}.
     *
     * @param mixinClassName the mixin's simple class name, as written in
     * {@code mariccolonytweaks.mixins.json}.
     * @return {@code false} only when a target is proven missing, so Mixin skips the mixin
     * and the feature degrades to vanilla MineColonies behaviour.
     */
    public static boolean shouldApply(final String mixinClassName)
    {
        final MixinFeature feature = MixinFeature.byMixinClassName(mixinClassName);
        if (feature == null)
        {
            LOGGER.warn("MaricColonyTweaks: {} is not in the compatibility table; applying it unguarded.", mixinClassName);
            return true;
        }

        final Failure failure = diagnose(feature);
        CHECKED.add(feature);
        if (failure == null)
        {
            return true;
        }

        if (DISABLED.putIfAbsent(feature, failure) == null)
        {
            LOGGER.error(
                "MaricColonyTweaks: incompatible with MineColonies {} - {} target not found ({}); feature '{}' DISABLED.",
                minecoloniesVersion(), mixinClassName, failure.detailed(), feature.featureName());
        }

        return false;
    }

    /**
     * Why a feature must be skipped, or {@code null} when it is fine to apply.
     *
     * <p>Dependencies are checked recursively so that a mixin calling into another mixin
     * (the courier log into the worker accessor) is reported as disabled too, rather than
     * linking against a member Mixin never generated - which would be a
     * {@code NoSuchMethodError} the moment that mixin ran.
     */
    private static Failure diagnose(final MixinFeature feature)
    {
        final Failure cached = DISABLED.get(feature);
        if (cached != null)
        {
            return cached;
        }

        for (final MixinFeature dependency : feature.requires())
        {
            final Failure reason = diagnose(dependency);
            if (reason != null)
            {
                DISABLED.putIfAbsent(dependency, reason);
                final String text = "dependency '" + dependency.featureName() + "' is disabled";
                return new Failure(text, text);
            }
        }

        final Class<?> target = loadTarget(feature, feature.targetClassName());
        if (target == null)
        {
            return null;
        }

        for (final Member member : feature.targets())
        {
            // A member may live on another class entirely - an @At(INVOKE) target owner, or
            // a shadowed constant - so resolve its own declaring class, not the mixin's.
            final Class<?> owner = member.resolveOwner(feature.targetClassName()).equals(feature.targetClassName())
                ? target
                : loadTarget(feature, member.resolveOwner(feature.targetClassName()));

            if (owner == null)
            {
                // Could not verify, so do not disable on a guess.
                continue;
            }

            if (!hasMember(owner, member, feature))
            {
                return new Failure(
                    member.detailedLabel(feature.targetClassName()),
                    member.shortLabel(feature.targetClassName()));
            }
        }

        return null;
    }

    /**
     * Loads a MineColonies class without initialising it - initialising a mod class this
     * early in boot would run its static initialisers at an arbitrary point in the game's
     * own startup.
     *
     * @return the class, or {@code null} when it cannot be loaded. {@code null} is
     * deliberately <em>not</em> treated as "missing": an unloadable class is a broken
     * classpath, not proof that the feature is unsupported.
     */
    private static Class<?> loadTarget(final MixinFeature feature, final String className)
    {
        try
        {
            return Class.forName(className, false, classLoader());
        }
        catch (final Throwable throwable)
        {
            LOGGER.warn("MaricColonyTweaks: could not load {} (needed by {}); that target is unverified and "
                + "its mixin is applied unguarded.", className, feature.mixinClassName(), throwable);
            return null;
        }
    }

    private static boolean hasMember(final Class<?> owner, final Member member, final MixinFeature feature)
    {
        try
        {
            return switch (member.kind())
            {
                case METHOD -> findMethod(owner, member) != null;
                case FIELD -> findField(owner, member) != null;
                case CONSTRUCTOR -> findConstructor(owner, member) != null;
            };
        }
        catch (final Throwable throwable)
        {
            // getDeclaredFields()/getDeclaredMethods() resolve every member's type, so a
            // single field typed against a mod that is not on the classpath takes the whole
            // lookup down. Unverified is not the same as missing, so stay fail-open - but
            // say so, because this is the one case where the guard goes quiet.
            LOGGER.warn("MaricColonyTweaks: could not inspect {} for {}; it is unverified, so the mixin is applied "
                + "unguarded. Usually means a MineColonies dependency is missing from the classpath.",
                member.detailedLabel(feature.targetClassName()), feature.mixinClassName(), throwable);
            return true;
        }
    }

    private static Method findMethod(final Class<?> owner, final Member member)
    {
        for (final Method method : owner.getDeclaredMethods())
        {
            if (method.getName().equals(member.name())
                && method.getParameterCount() == member.parameterTypeNames().size()
                && matches(method.getParameterTypes(), member.parameterTypeNames()))
            {
                return reveal(method);
            }
        }

        return null;
    }

    private static Constructor<?> findConstructor(final Class<?> owner, final Member member)
    {
        for (final Constructor<?> constructor : owner.getDeclaredConstructors())
        {
            if (constructor.getParameterCount() == member.parameterTypeNames().size()
                && matches(constructor.getParameterTypes(), member.parameterTypeNames()))
            {
                return reveal(constructor);
            }
        }

        return null;
    }

    private static Field findField(final Class<?> owner, final Member member)
    {
        for (final Field field : owner.getDeclaredFields())
        {
            if (field.getName().equals(member.name())
                && field.getType().getName().equals(member.parameterTypeNames().get(0)))
            {
                return reveal(field);
            }
        }

        return null;
    }

    private static boolean matches(final Class<?>[] actual, final List<String> expected)
    {
        for (int i = 0; i < expected.size(); i++)
        {
            if (!actual[i].getName().equals(expected.get(i)))
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Confirms the guard can actually see into the target. Mixin reaches these members by
     * patching bytecode rather than by reflection, so a failure here is not fatal - but it
     * is worth knowing about, hence a debug log rather than a silent pass.
     */
    private static <T extends AccessibleObject> T reveal(final T member)
    {
        try
        {
            member.setAccessible(true);
        }
        catch (final Throwable throwable)
        {
            LOGGER.debug("MaricColonyTweaks: setAccessible failed on {}", describe(member), throwable);
        }

        return member;
    }

    /**
     * The MineColonies version in use, read from the mod metadata on the classpath.
     *
     * <p>Mod metadata rather than {@code ModList}, because this runs while Mixin prepares
     * its config - long before FML exists.
     *
     * @return the declared version, or {@code "unknown"}. Never throws: a guard must not be
     * able to fail the boot on its own.
     */
    public static String minecoloniesVersion()
    {
        String version = minecoloniesVersion;
        if (version == null)
        {
            synchronized (MixinCompatibility.class)
            {
                version = minecoloniesVersion;
                if (version == null)
                {
                    version = readMinecoloniesVersion();
                    minecoloniesVersion = version;
                }
            }
        }

        return version;
    }

    private static String readMinecoloniesVersion()
    {
        for (final String metadata : List.of("META-INF/neoforge.mods.toml", "META-INF/mods.toml"))
        {
            final String version = scanModMetadata(metadata);
            if (version != null)
            {
                return version;
            }
        }

        return UNKNOWN_VERSION;
    }

    /**
     * Finds MineColonies' {@code [[mods]]} table among all mod metadata visible on the
     * classpath and returns its {@code version}. A hand-rolled scan rather than a TOML
     * parser, because the guard may not add dependencies.
     */
    private static String scanModMetadata(final String resource)
    {
        try
        {
            final Enumeration<URL> urls = classLoader().getResources(resource);
            while (urls.hasMoreElements())
            {
                final URL url = urls.nextElement();
                try (InputStream in = url.openStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
                {
                    final String version = readVersion(reader);
                    if (version != null)
                    {
                        LOGGER.debug("MaricColonyTweaks: MineColonies {} read from {}", version, url);
                        return version;
                    }
                }
            }
        }
        catch (final Throwable throwable)
        {
            LOGGER.debug("MaricColonyTweaks: could not read {} from the classpath", resource, throwable);
        }

        return null;
    }

    private static String readVersion(final BufferedReader reader) throws IOException
    {
        boolean inForeignModsTable = false;
        String line;
        while ((line = reader.readLine()) != null)
        {
            final String entry = stripComment(line).trim();
            if (entry.isEmpty())
            {
                continue;
            }

            if (entry.startsWith("[[mods]]"))
            {
                inForeignModsTable = true;
                continue;
            }

            if (entry.startsWith("["))
            {
                // Any other table ends whatever [[mods]] block we were in.
                inForeignModsTable = false;
                continue;
            }

            if (!inForeignModsTable)
            {
                continue;
            }

            final Matcher id = MOD_ID.matcher(entry);
            if (id.matches())
            {
                if (!MINECOLONIES_MOD_ID.equals(id.group(1)))
                {
                    // Somebody else's [[mods]] table; keep looking for MineColonies' own.
                    inForeignModsTable = false;
                }

                continue;
            }

            final Matcher version = VERSION.matcher(entry);
            if (version.matches())
            {
                return version.group(1);
            }
        }

        return null;
    }

    /** Drops a trailing {@code # comment} that is not inside a quoted string. */
    private static String stripComment(final String line)
    {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++)
        {
            final char c = line.charAt(i);
            if (c == '"')
            {
                quoted = !quoted;
            }
            else if (c == '#' && !quoted)
            {
                return line.substring(0, i);
            }
        }

        return line;
    }

    /** Logs the detected MineColonies version exactly once per JVM. */
    public static void logDetectedVersion()
    {
        if (versionLogged)
        {
            return;
        }

        versionLogged = true;
        LOGGER.info("MaricColonyTweaks: MineColonies {} detected; {} MaricColonyTweaks mixin features to verify.",
            minecoloniesVersion(), MixinFeature.values().length);
    }

    /**
     * One-line summary of what is active and what is not. Only features Mixin actually
     * asked about are counted - on a dedicated server the client mixin never runs, and
     * reporting it as active would be a lie.
     */
    public static void logSummary()
    {
        final List<MixinFeature> active = activeFeatures();
        final List<MixinFeature> disabled = disabledFeatures();
        final int total = active.size() + disabled.size();

        if (total == 0)
        {
            LOGGER.warn("MaricColonyTweaks: no mixin features were checked.");
            return;
        }

        final String summary = "MaricColonyTweaks: " + active.size() + "/" + total
            + " features active on MineColonies " + minecoloniesVersion()
            + "; disabled: " + (disabled.isEmpty()
                ? "none"
                : disabled.stream().map(MixinFeature::featureName).collect(Collectors.joining(", ")));

        if (disabled.isEmpty())
        {
            LOGGER.info(summary);
        }
        else
        {
            LOGGER.warn(summary);
        }
    }

    /** Features whose targets were verified and will be applied. */
    public static List<MixinFeature> activeFeatures()
    {
        return checked(false);
    }

    /**
     * Features that were skipped because a MineColonies target moved, renamed or changed
     * shape. Each falls back to vanilla MineColonies behaviour.
     */
    public static List<MixinFeature> disabledFeatures()
    {
        return checked(true);
    }

    /**
     * One {@code feature - missing target} entry per disabled feature, in the compact form
     * that fits in chat. The log line carries the fully qualified detail.
     */
    public static List<String> disabledReport()
    {
        final List<String> report = new ArrayList<>();
        for (final MixinFeature feature : disabledFeatures())
        {
            report.add(feature.featureName() + " - " + DISABLED.get(feature).shortLabel());
        }

        return Collections.unmodifiableList(report);
    }

    private static List<MixinFeature> checked(final boolean wantDisabled)
    {
        final List<MixinFeature> result = new ArrayList<>();
        for (final MixinFeature feature : MixinFeature.values())
        {
            if (CHECKED.contains(feature) && DISABLED.containsKey(feature) == wantDisabled)
            {
                result.add(feature);
            }
        }

        return Collections.unmodifiableList(result);
    }

    private static String describe(final AccessibleObject member)
    {
        return member instanceof final Method method
            ? method.getDeclaringClass().getName() + "#" + method.getName()
            : member instanceof final Field field
                ? field.getDeclaringClass().getName() + "#" + field.getName()
                : member instanceof final Constructor<?> constructor
                    ? constructor.getDeclaringClass().getName() + "#<init>"
                    : member.getClass().getName();
    }

    private static ClassLoader classLoader()
    {
        final ClassLoader loader = MixinCompatibility.class.getClassLoader();
        return loader == null ? ClassLoader.getSystemClassLoader() : loader;
    }

    /**
     * {@link #diagnose} recurses through {@link MixinFeature#requires()}, so a
     * cycle in the table would be a stack overflow at boot. Check the table once, here.
     */
    static
    {
        for (final MixinFeature feature : MixinFeature.values())
        {
            for (final MixinFeature dependency : feature.requires())
            {
                if (dependency == feature || dependency.requires().contains(feature))
                {
                    throw new IllegalStateException("Circular dependency between "
                        + feature.mixinClassName() + " and " + dependency.mixinClassName());
                }
            }
        }
    }
}
