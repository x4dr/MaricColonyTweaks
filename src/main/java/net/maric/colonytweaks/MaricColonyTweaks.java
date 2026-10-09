package net.maric.colonytweaks;

import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.colony.buildings.modules.settings.SettingKey;
import net.maric.colonytweaks.compat.CompatibilityNotifier;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;

@Mod("mariccolonytweaks")
public final class MaricColonyTweaks
{
    public static final org.slf4j.Logger LOGGER =
        com.mojang.logging.LogUtils.getLogger();

    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec.IntValue STUCK_MS;
    public static final ModConfigSpec.IntValue MAX_RETRIES;
    public static final ModConfigSpec.BooleanValue ENABLE_STUDENT_SWAPS;
    public static final ModConfigSpec.BooleanValue ENABLE_RESEARCH_REQUESTS;

    /**
     * Farmer hut setting: whether the farmer may request Bone Meal as fertilizer.
     * When disabled the fertilizer request only asks for Compost, so a colony
     * connected to external storage (e.g. the Create bridge) no longer drains it.
     */
    public static final SettingKey<BoolSetting> USE_BONEMEAL =
        new SettingKey<>(BoolSetting.class, ResourceLocation.fromNamespaceAndPath("mariccolonytweaks", "use_bonemeal"));

    static
    {
        final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        STUCK_MS = builder
            .comment("How long (ms) a builder may fail to position for one block before MaricColonyTweaks forces it to proceed.",
                "Lower = builders cycle through stuck blocks faster; very low values can cause false positives while a builder is legitimately walking to a distant block.")
            .defineInRange("stuckMs", 5000, 500, 600000);
        MAX_RETRIES = builder
            .comment("How many force attempts MaricColonyTweaks may make on the same block before giving up on it for a while.",
                "A stuck builder is often stuck on the next block too, so low values keep it cycling instead of wasting a full workday on one block.")
            .defineInRange("maxRetries", 2, 1, 20);
        ENABLE_STUDENT_SWAPS = builder
            .comment("When a Library Student reaches the study cap (happiness-based Intelligence cap), move them out of the Library.",
                "First choice: an open auto-hire job slot in the colony (the Library is left open).",
                "Otherwise: swap jobs with the lowest-Intelligence employed citizen still under the cap, who becomes the Student.",
                "Guards and Undertakers are never involved.")
            .define("enableStudentSwaps", true);
        ENABLE_RESEARCH_REQUESTS = builder
            .comment("When clicking a university research whose cost items are not in the university chest or your inventory,",
                "the university creates colony requests for the missing items (couriers fetch them from the Warehouse).",
                "The research then starts automatically once everything has been delivered.",
                "When disabled, vanilla behavior applies (research simply fails with 'cost not available').")
            .define("enableResearchRequests", true);
        COMMON_SPEC = builder.build();
    }

    public MaricColonyTweaks(final IEventBus modBus, final ModContainer modContainer)
    {
        modContainer.registerConfig(ModConfig.Type.COMMON, COMMON_SPEC, "mariccolonytweaks-common.toml");

        // Player events live on the game bus, not the mod bus, so this is the server-side
        // (and therefore singleplayer-safe) place to report the mixin compatibility result.
        NeoForge.EVENT_BUS.addListener(CompatibilityNotifier::onPlayerLoggedIn);
        CompatibilityNotifier.logSummary();
    }
}
