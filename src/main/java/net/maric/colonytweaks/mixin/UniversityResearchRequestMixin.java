package net.maric.colonytweaks.mixin;

import com.minecolonies.api.research.IGlobalResearch;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingUniversity;
import com.minecolonies.core.network.messages.server.colony.building.university.TryResearchMessage;
import net.maric.colonytweaks.MaricColonyTweaks;
import net.maric.colonytweaks.UniversityResearch;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * University research item requests (click path).
 *
 * <p>Vanilla TryResearchMessage only starts a research when the full cost is present in
 * the university chest + the clicking player's inventory. When MaricColonyTweaks research
 * requests are enabled, a click with missing items instead creates colony requests for
 * the missing amounts (couriers fetch them from the Warehouse); the research then starts
 * automatically once everything has been delivered (see BuildingUniversityResearchMixin).
 */
@Mixin(TryResearchMessage.class)
public abstract class UniversityResearchRequestMixin
{
    @Shadow(remap = false)
    private ResourceLocation researchId;

    @Shadow(remap = false)
    private ResourceLocation branch;

    @Shadow(remap = false)
    private boolean reset;

    @Inject(
        method = "onExecute(Lnet/neoforged/neoforge/network/handling/IPayloadContext;Lnet/minecraft/server/level/ServerPlayer;Lcom/minecolonies/api/colony/IColony;Lcom/minecolonies/core/colony/buildings/workerbuildings/BuildingUniversity;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private void mariccolonytweaks$requestMissingItems(
        final IPayloadContext ctx,
        final ServerPlayer player,
        final com.minecolonies.api.colony.IColony colony,
        final BuildingUniversity building,
        final CallbackInfo ci)
    {
        if (this.reset || player.isCreative() || !MaricColonyTweaks.ENABLE_RESEARCH_REQUESTS.get())
        {
            return;
        }

        final IGlobalResearch research = IGlobalResearchTree.getInstance().getResearch(this.branch, this.researchId);
        if (research == null)
        {
            return;
        }

        // Vanilla handles the "not researchable / already in progress" cases itself.
        if (!research.canResearch(building, colony.getResearchManager().getResearchTree())
            || colony.getResearchManager().getResearchTree().getResearch(this.branch, this.researchId) != null)
        {
            return;
        }

        if (research.hasEnoughResources(player, building.getPosition()))
        {
            return;
        }

        UniversityResearch.createMissingRequests(player, colony, building, research);
        ci.cancel();
    }
}
