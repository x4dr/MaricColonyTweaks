package net.maric.colonytweaks.mixin;

import com.ldtteam.blockui.Pane;
import com.ldtteam.blockui.controls.Button;
import com.ldtteam.blockui.controls.ButtonImage;
import com.ldtteam.blockui.views.ZoomDragView;
import com.minecolonies.api.research.IGlobalResearch;
import com.minecolonies.api.research.IGlobalResearchTree;
import com.minecolonies.api.research.ILocalResearch;
import com.minecolonies.api.research.ILocalResearchTree;
import com.minecolonies.api.research.util.ResearchState;
import com.minecolonies.core.client.gui.WindowResearchTree;
import com.minecolonies.core.network.messages.server.colony.building.university.TryResearchMessage;
import net.maric.colonytweaks.MaricColonyTweaks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;

/**
 * University research item requests (client click path).
 *
 * <p>Vanilla never makes a "cost missing" research tile clickable: the button ID is only
 * set for AVAILABLE / IN_PROGRESS / FINISHED tiles, so a click on a cost-missing tile
 * reaches onButtonClicked with an empty ID and is silently dropped. The in-progress cap
 * (university level) is likewise a silent dead end in the click guard.
 *
 * <p>This mixin fixes both: cost-missing tiles get their ID set like available ones, the
 * in-progress cap check is bypassed while MaricColonyTweaks requests are enabled (the queued
 * research only actually progresses after delivery, throttled by the cap as usual), and
 * the cost check redirect sends TryResearchMessage to the server when the cost is short,
 * where the server-side mixin cancels the start and creates the colony requests.
 */
@Mixin(WindowResearchTree.class)
public abstract class WindowResearchTreeRequestMixin
{
    @Shadow(remap = false)
    private com.minecolonies.api.colony.buildings.views.IBuildingView building;

    @Shadow(remap = false)
    private ResourceLocation branch;

    @Shadow(remap = false)
    private boolean hasMax;

    /**
     * MISSING_COST tiles: re-derive the state (the enum is package-private and cannot
     * appear in a mixin signature) and set the same button IDs vanilla sets for
     * available tiles, so clicks identify the research.
     */
    @Inject(
        method = "drawResearchItem",
        at = @At("RETURN"),
        remap = false
    )
    private void mariccolonytweaks$makeCostMissingTilesClickable(
        final ZoomDragView view,
        final int offsetX,
        final int offsetY,
        final IGlobalResearch research,
        final boolean abandoned,
        final org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir)
    {
        if (!MaricColonyTweaks.ENABLE_RESEARCH_REQUESTS.get() || Minecraft.getInstance().player.isCreative())
        {
            return;
        }

        final ILocalResearchTree tree = this.building.getColony().getResearchManager().getResearchTree();
        final ILocalResearch local = tree.getResearch(this.branch, research.getId());
        if (local != null && (local.getState() == ResearchState.FINISHED || local.getState() == ResearchState.IN_PROGRESS))
        {
            return;
        }

        // Mirror getResearchButtonState's branch order up to the cost check.
        if (!tree.hasCompletedResearch(research.getParent()))
        {
            return; // MISSING_PARENT
        }

        final int uniLevel = this.building.getBuildingLevel();
        final int uniMax = this.building.getBuildingMaxLevel();
        if (research.getDepth() > uniLevel
            && (research.getDepth() <= uniMax || this.hasMax || uniLevel != uniMax)
            && IGlobalResearchTree.getInstance().getBranchData(this.branch).getType() != com.minecolonies.api.research.ResearchBranchType.UNLOCKABLES)
        {
            return; // TOO_LOW_UNIVERSITY
        }

        if (!IGlobalResearchTree.getInstance().isResearchRequirementsFulfilled(research.getResearchRequirements(), this.building.getColony()))
        {
            return; // MISSING_REQUIREMENT
        }

        if (research.hasEnoughResources(Minecraft.getInstance().player, this.building.getPosition()))
        {
            return; // AVAILABLE - vanilla already set the IDs
        }

        // MISSING_COST: locate this tile's two buttons by their drawn position.
        for (final Pane child : view.getChildren())
        {
            if (child instanceof ButtonImage button && button.getID().isEmpty())
            {
                if (button.getX() == offsetX && button.getY() == offsetY)
                {
                    button.setID(research.getId().toString());
                }
                else if (button.getX() == offsetX - 3 && button.getY() == offsetY - 4)
                {
                    button.setID(research.getId().toString());
                }
            }
        }
    }

    @Redirect(
        method = "onButtonClicked",
        at = @At(
            value = "INVOKE",
            target = "Lcom/minecolonies/api/research/IGlobalResearch;hasEnoughResources(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;)Z"),
        remap = false
    )
    private boolean mariccolonytweaks$requestOnMissingCost(
        final IGlobalResearch research,
        final Player player,
        final BlockPos pos)
    {
        final boolean enough = research.hasEnoughResources(player, pos);
        if (!enough && !player.isCreative() && MaricColonyTweaks.ENABLE_RESEARCH_REQUESTS.get())
        {
            new TryResearchMessage(this.building, research.getId(), research.getBranch(), false).sendToServer();
        }
        return enough;
    }
}
