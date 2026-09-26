package net.maric.colonytweaks.compat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The MaricColonyTweaks compatibility table: one entry per mixin listed in
 * {@code mariccolonytweaks.mixins.json}, with the MineColonies members it depends on and the
 * human-readable feature name used in the log and the in-game report.
 *
 * <p>The mixins are <em>not</em> generated from this table. It exists so that
 * {@link net.maric.colonytweaks.mixin.ColonyTweaksMixinPlugin} can verify every target by
 * reflection <em>before</em> Mixin attempts to apply the mixin, so a MineColonies update
 * that moves or renames something degrades to "feature off" instead of a boot crash.
 *
 * <p>Only MineColonies (and the BlockUI classes it hands to MaricColonyTweaks) targets are listed.
 * Minecraft / NeoForge targets are left to the {@code require = 0} injectors: a change
 * there is a different kind of incompatibility and should not be reported as
 * "MineColonies unsupported".
 */
public enum MixinFeature
{
    /**
     * Support mixin: reads the inherited {@code AbstractAISkeleton.worker} field. Not a
     * user-visible feature on its own, but {@link #COURIER_CHEST_FULL_LOG} calls into it,
     * so a failure here must take that feature down as well - otherwise that mixin would
     * link against an accessor that was never generated.
     */
    CITIZEN_WORKER_ACCESSOR(
        "AbstractAISkeletonAccessor",
        "citizen worker accessor",
        "com.minecolonies.core.entity.ai.workers.AbstractAISkeleton",
        new Member[]{
            Member.field("worker", "com.minecolonies.api.entity.citizen.AbstractEntityCitizen"),
        },
        new MixinFeature[0],
        false),

    BUILDER_UNSTICK(
        "BuilderUnstickMixin",
        "builder unstick",
        "com.minecolonies.core.entity.ai.workers.builder.EntityAIStructureBuilder",
        new Member[]{
            Member.method("walkToConstructionSite", "net.minecraft.core.BlockPos"),
        },
        new MixinFeature[0],
        false),

    COURIER_CHEST_FULL_LOG(
        "CourierFullChestLogMixin",
        "courier full-chest logging",
        "com.minecolonies.core.entity.ai.workers.service.EntityAIWorkDeliveryman",
        new Member[]{
            Member.method("deliver"),
            Member.foreignMethod("com.minecolonies.api.colony.buildings.ISchematicProvider", "getSchematicName"),
        },
        new MixinFeature[]{CITIZEN_WORKER_ACCESSOR},
        false),

    FARMER_BONEMEAL_SETTING(
        "FarmerBonemealSettingMixin",
        "per-hut Use Bone Meal setting",
        "com.minecolonies.core.colony.buildings.modules.SettingsModule",
        new Member[]{
            Member.method("with",
                "com.minecolonies.api.colony.buildings.modules.settings.ISettingKey",
                "com.minecolonies.api.colony.buildings.modules.settings.ISetting"),
            Member.foreignField("com.minecolonies.core.colony.buildings.workerbuildings.BuildingFarmer",
                "FERTILIZE",
                "com.minecolonies.api.colony.buildings.modules.settings.ISettingKey"),
        },
        new MixinFeature[0],
        false),

    FARMER_BONEMEAL_REQUEST(
        "FarmerBonemealRequestMixin",
        "bone meal-free fertilizer requests",
        "com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkFarmer",
        new Member[]{
            Member.method("prepareForFarming"),
            // Declared as the type variable B, so reflection sees its erasure.
            Member.foreignField("com.minecolonies.core.entity.ai.workers.AbstractEntityAIBasic",
                "building",
                "com.minecolonies.core.colony.buildings.AbstractBuilding"),
            Member.foreignConstructor("com.minecolonies.api.colony.requestsystem.requestable.StackList",
                "java.util.List", "java.lang.String", "int", "int"),
        },
        new MixinFeature[0],
        false),

    STUDENT_GRADUATION(
        "StudentGraduationMixin",
        "library student graduation",
        "com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule",
        new Member[]{
            Member.method("onColonyTick", "com.minecolonies.api.colony.IColony"),
        },
        new MixinFeature[0],
        false),

    UNIVERSITY_RESEARCH_REQUEST(
        "UniversityResearchRequestMixin",
        "university research item requests",
        "com.minecolonies.core.network.messages.server.colony.building.university.TryResearchMessage",
        new Member[]{
            Member.method("onExecute",
                "net.neoforged.neoforge.network.handling.IPayloadContext",
                "net.minecraft.server.level.ServerPlayer",
                "com.minecolonies.api.colony.IColony",
                "com.minecolonies.core.colony.buildings.workerbuildings.BuildingUniversity"),
            Member.field("researchId", "net.minecraft.resources.ResourceLocation"),
            Member.field("branch", "net.minecraft.resources.ResourceLocation"),
            Member.field("reset", "boolean"),
        },
        new MixinFeature[0],
        false),

    UNIVERSITY_RESEARCH_TICK(
        "BuildingUniversityResearchMixin",
        "university research auto-start",
        "com.minecolonies.core.colony.buildings.workerbuildings.BuildingUniversity",
        new Member[]{
            Member.method("onColonyTick", "com.minecolonies.api.colony.IColony"),
            Member.method("serializeNBT", "net.minecraft.core.HolderLookup$Provider"),
            Member.method("deserializeNBT",
                "net.minecraft.core.HolderLookup$Provider",
                "net.minecraft.nbt.CompoundTag"),
        },
        new MixinFeature[0],
        false),

    RESEARCH_TREE_CLICK(
        "WindowResearchTreeRequestMixin",
        "clickable cost-missing research tiles",
        "com.minecolonies.core.client.gui.WindowResearchTree",
        new Member[]{
            Member.method("drawResearchItem",
                "com.ldtteam.blockui.views.ZoomDragView",
                "int",
                "int",
                "com.minecolonies.api.research.IGlobalResearch",
                "boolean"),
            Member.method("onButtonClicked", "com.ldtteam.blockui.controls.Button"),
            Member.field("building", "com.minecolonies.api.colony.buildings.views.IBuildingView"),
            Member.field("branch", "net.minecraft.resources.ResourceLocation"),
            Member.field("hasMax", "boolean"),
        },
        new MixinFeature[0],
        true);

    private static final Map<String, MixinFeature> BY_MIXIN = index();

    private final String mixinClassName;
    private final String featureName;
    private final String targetClassName;
    private final List<Member> targets;
    private final List<MixinFeature> requires;
    private final boolean clientOnly;

    MixinFeature(
        final String mixinClassName,
        final String featureName,
        final String targetClassName,
        final Member[] targets,
        final MixinFeature[] requires,
        final boolean clientOnly)
    {
        this.mixinClassName = mixinClassName;
        this.featureName = featureName;
        this.targetClassName = targetClassName;
        this.targets = List.of(targets);
        this.requires = List.of(requires);
        this.clientOnly = clientOnly;
    }

    private static Map<String, MixinFeature> index()
    {
        final Map<String, MixinFeature> map = new LinkedHashMap<>();
        for (final MixinFeature feature : values())
        {
            map.put(feature.mixinClassName, feature);
        }

        return Collections.unmodifiableMap(map);
    }

    /**
     * Resolves a mixin by its simple class name, which is how {@code mariccolonytweaks.mixins.json}
     * and {@code shouldApplyMixin} refer to it.
     *
     * @return the feature, or {@code null} when the mixin is not in the table - in which
     * case the caller must not disable it, because the guard knows nothing about it.
     */
    public static MixinFeature byMixinClassName(final String simpleName)
    {
        return BY_MIXIN.get(simpleName);
    }

    public String mixinClassName()
    {
        return this.mixinClassName;
    }

    public String featureName()
    {
        return this.featureName;
    }

    public String targetClassName()
    {
        return this.targetClassName;
    }

    public List<Member> targets()
    {
        return this.targets;
    }

    public List<MixinFeature> requires()
    {
        return this.requires;
    }

    public boolean clientOnly()
    {
        return this.clientOnly;
    }

    /** The fully-qualified name of this feature's mixin class. */
    public String qualifiedMixinClassName()
    {
        return "net.maric.colonytweaks.mixin." + this.mixinClassName;
    }

    /**
     * A MineColonies class or member that one of MaricColonyTweaks's mixins depends on.
     *
     * <p>Names are kept as strings on purpose: the guard runs while Mixin prepares the
     * config, very early in boot, and must not be the thing that forces extra class
     * loading to happen at the wrong moment. {@link MixinCompatibility} resolves the
     * declaring class with {@code Class.forName(name, false, loader)}.
     */
    public record Member(Kind kind, String ownerClassName, String name, List<String> parameterTypeNames)
    {
        public enum Kind
        {
            METHOD,
            FIELD,
            CONSTRUCTOR
        }

        /** A method on the feature's own target class. */
        public static Member method(final String name, final String... parameterTypeNames)
        {
            return new Member(Kind.METHOD, null, name, List.of(parameterTypeNames));
        }

        /** A field on the feature's own target class. */
        public static Member field(final String name, final String typeName)
        {
            return new Member(Kind.FIELD, null, name, List.of(typeName));
        }

        /** A method on some other class, e.g. the owner of an {@code @At(INVOKE)} target. */
        public static Member foreignMethod(final String ownerClassName, final String name, final String... parameterTypeNames)
        {
            return new Member(Kind.METHOD, ownerClassName, name, List.of(parameterTypeNames));
        }

        /** A field on some other class, e.g. a shadowed constant. */
        public static Member foreignField(final String ownerClassName, final String name, final String typeName)
        {
            return new Member(Kind.FIELD, ownerClassName, name, List.of(typeName));
        }

        /** A constructor on some other class, e.g. the {@code new StackList(...)} an {@code @At} redirects. */
        public static Member foreignConstructor(final String ownerClassName, final String... parameterTypeNames)
        {
            return new Member(Kind.CONSTRUCTOR, ownerClassName, "<init>", List.of(parameterTypeNames));
        }

        /** The class this member must be declared on. */
        public String resolveOwner(final String featureTargetClassName)
        {
            return this.ownerClassName == null ? featureTargetClassName : this.ownerClassName;
        }

        /** Compact {@code Class#member} form, for the in-game report. */
        public String shortLabel(final String featureTargetClassName)
        {
            return simpleName(this.resolveOwner(featureTargetClassName)) + "#" + this.name;
        }

        /** Fully qualified form, for the log file. */
        public String detailedLabel(final String featureTargetClassName)
        {
            final StringBuilder sb = new StringBuilder(this.resolveOwner(featureTargetClassName));
            sb.append('#').append(this.name);
            if (this.kind != Kind.FIELD)
            {
                sb.append('(');
                for (int i = 0; i < this.parameterTypeNames.size(); i++)
                {
                    if (i > 0)
                    {
                        sb.append(", ");
                    }

                    sb.append(this.parameterTypeNames.get(i));
                }

                sb.append(')');
            }

            return sb.toString();
        }

        private static String simpleName(final String className)
        {
            final int lastDot = className.lastIndexOf('.');
            return lastDot < 0 ? className : className.substring(lastDot + 1);
        }
    }
}
