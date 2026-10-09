package net.maric.colonytweaks.compat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two config flags the whole compatibility story rests on, and the table's agreement with
 * the mixin config.
 *
 * <p>Nothing in this mod checks whether a MineColonies target still exists - Mixin does, at apply
 * time. That only produces a reportable failure if {@code injectors.defaultRequire} is 1, and
 * it only stays non-fatal if the config is {@code required: false}. Both are read from the real
 * config file, so changing either one and forgetting the other fails here rather than in a
 * player's log.
 */
@DisplayName("The mixin config is set up so a broken feature is both visible and non-fatal")
class MixinConfigAgreementTest
{
    @Test
    @DisplayName("defaultRequire is 1, so a moved injection target fails loudly")
    void defaultRequireIsOne()
    {
        assertEquals(1, MixinConfigNames.defaultRequire(),
            "at 0 an injection whose target moved is silently skipped and the feature still "
                + "reports as loaded, which is the silent degradation this mod is meant to avoid");
    }

    @Test
    @DisplayName("the config is required: false, so that loud failure still lets the game boot")
    void configIsNotRequired()
    {
        assertTrue(!MixinConfigNames.isRequired(),
            "with required: true a MineColonies update that moves an injection target would "
                + "abort the boot instead of dropping one feature");
    }

    @Test
    @DisplayName("every mixin in the config has a feature entry, or it could never be reported")
    void everyConfigMixinHasAFeatureEntry()
    {
        final List<String> configMixins = allConfigMixins();
        for (final String mixin : configMixins)
        {
            final boolean isSupportMixin = mixin.equals("AbstractAISkeletonAccessor");
            assertEquals(!isSupportMixin,
                MixinFeature.byMixinClassName(mixin) != null,
                mixin + " has no MixinFeature entry, so AppliedFeatures would silently drop it "
                    + "and it could never appear in the report as broken");
        }
    }

    @Test
    @DisplayName("no feature entry is missing from the config, or it could never be applied")
    void everyFeatureEntryIsInTheConfig()
    {
        final List<String> configMixins = allConfigMixins();
        for (final MixinFeature feature : MixinFeature.values())
        {
            assertTrue(configMixins.contains(feature.mixinClassName()),
                feature.featureName() + " (" + feature.mixinClassName() + ") is in the table but "
                    + "not in mariccolonytweaks.mixins.json, so its mixin would never be applied");
        }
    }

    @Test
    @DisplayName("feature names are unique and non-blank, since they are the in-game report")
    void featureNamesAreUniqueAndNonBlank()
    {
        for (final MixinFeature feature : MixinFeature.values())
        {
            assertNotNull(feature.featureName());
            assertTrue(!feature.featureName().isBlank(),
                feature.mixinClassName() + " has a blank feature name");
        }

        for (final MixinFeature feature : MixinFeature.values())
        {
            long duplicates = 0;
            for (final MixinFeature other : MixinFeature.values())
            {
                if (other.featureName().equals(feature.featureName()))
                {
                    duplicates++;
                }
            }

            assertEquals(1, duplicates,
                "\"" + feature.featureName() + "\" is used by more than one feature, so the "
                    + "player would not be able to tell which one is broken");
        }
    }

    @Test
    @DisplayName("the client mixin is listed under client, not mixins")
    void clientMixinIsInTheClientSection()
    {
        assertTrue(MixinConfigNames.clientMixins().contains(MixinFeature.RESEARCH_TREE_CLICK.mixinClassName()),
            "a client-only mixin in the common section would be reported as loaded on a "
                + "dedicated server, where it never runs");
    }

    /** Both arrays, which together are every mixin Mixin will consider. */
    private static List<String> allConfigMixins()
    {
        final List<String> all = new java.util.ArrayList<>(MixinConfigNames.mixinsInConfigOrder());
        all.addAll(MixinConfigNames.clientMixins());
        return all;
    }
}