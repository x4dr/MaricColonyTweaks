package net.maric.colonytweaks.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.requestsystem.request.RequestState;
import com.minecolonies.api.colony.requestsystem.token.IToken;
import com.minecolonies.core.colony.requestsystem.management.manager.StandardRequestManager;
import net.maric.colonytweaks.UniversityResearch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Makes cancelling a research item delivery actually cancel the research request.
 *
 * <p>{@code updateRequestState} is the single choke point every cancel path funnels through -
 * the clipboard's cancel button ({@code RequestTreeWindowModule} sends
 * {@code UpdateRequestStateMessage}), citizen interactions, and the building menus. Before this
 * mixin, cancelling one of the deliveries a pending research asked for only removed that one
 * request: the university tick re-armed it on the next pass, roughly 25 s later, so the cancel
 * looked like it had done nothing.
 *
 * <p>{@code HEAD} rather than {@code TAIL} so the marker is cleared before the cancellation
 * cascades to the research's other deliveries.
 */
@Mixin(StandardRequestManager.class)
public abstract class ResearchRequestCancelMixin
{
    @Inject(
        method = "updateRequestState",
        at = @At("HEAD"),
        remap = false
    )
    private void mariccolonytweaks$cancelPendingResearch(
        final IToken<?> token,
        final RequestState state,
        final CallbackInfo ci)
    {
        if (state != RequestState.CANCELLED)
        {
            return;
        }

        final IColony colony = ((StandardRequestManager) (Object) this).getColony();
        if (colony == null)
        {
            return;
        }

        UniversityResearch.onRequestCancelled(colony, token);
    }
}
