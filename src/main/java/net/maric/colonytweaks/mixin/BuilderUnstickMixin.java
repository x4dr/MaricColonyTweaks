package net.maric.colonytweaks.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.ai.workers.builder.EntityAIStructureBuilder;
import net.maric.colonytweaks.MaricColonyTweaks;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Watchdog for the MineColonies builder pathfinding loop where the worker keeps
 * failing to step up onto a block and retries for the whole day.
 *
 * If the builder spends "stuckMs" unable to position itself for the same target
 * block while barely moving, force it to proceed so the AI attempts the actual
 * block placement. Repeat up to "maxRetries" per block, then back off and try
 * again later. Thresholds come from the mariccolonytweaks-common.toml config and are read
 * live, so changes made in-game apply without a restart.
 */
@Mixin(EntityAIStructureBuilder.class)
public abstract class BuilderUnstickMixin
{
    /**
     * After giving up on a block, wait this long before allowing a new retry round.
     */
    @Unique
    private static final long GIVEUP_RETRY_MS = 30_000L;

    /**
     * If the builder moved at least this far (blocks, squared-XZ) between consecutive
     * AI ticks it counts as "still trying to walk", not stuck.
     */
    @Unique
    private static final double MOVED_EPSILON_SQ = 0.25 * 0.25;

    @Unique
    private BlockPos mariccolonytweaks_target;

    @Unique
    private long mariccolonytweaks_stalledSinceMs;

    @Unique
    private long mariccolonytweaks_lastForceMs;

    @Unique
    private int mariccolonytweaks_forceCount;

    @Unique
    private boolean mariccolonytweaks_gaveUp;

    @Unique
    private double mariccolonytweaks_prevX;

    @Unique
    private double mariccolonytweaks_prevZ;

    @Unique
    private boolean mariccolonytweaks_hasPrev;

    @Inject(method = "walkToConstructionSite", at = @At("RETURN"), cancellable = true)
    private void mariccolonytweaks$builderUnstick(final BlockPos currentBlock, final CallbackInfoReturnable<Boolean> cir)
    {
        if (cir.getReturnValueZ())
        {
            mariccolonytweaks_target = null;
            mariccolonytweaks_forceCount = 0;
            mariccolonytweaks_gaveUp = false;
            mariccolonytweaks_hasPrev = false;
            return;
        }
        if (currentBlock == null)
        {
            return;
        }

        final AbstractEntityCitizen worker = ((EntityAIStructureBuilder) (Object) this).getWorker();
        if (worker == null)
        {
            return;
        }
        final long now = System.currentTimeMillis();
        final double wx = worker.getX();
        final double wz = worker.getZ();

        if (mariccolonytweaks_target == null || !currentBlock.equals(mariccolonytweaks_target))
        {
            mariccolonytweaks_target = currentBlock;
            mariccolonytweaks_stalledSinceMs = now;
            mariccolonytweaks_forceCount = 0;
            mariccolonytweaks_gaveUp = false;
            mariccolonytweaks_prevX = wx;
            mariccolonytweaks_prevZ = wz;
            mariccolonytweaks_hasPrev = true;
            return;
        }

        if (mariccolonytweaks_hasPrev)
        {
            final double dx = wx - mariccolonytweaks_prevX;
            final double dz = wz - mariccolonytweaks_prevZ;
            if (dx * dx + dz * dz >= MOVED_EPSILON_SQ)
            {
                mariccolonytweaks_stalledSinceMs = now;
            }
        }
        mariccolonytweaks_prevX = wx;
        mariccolonytweaks_prevZ = wz;
        mariccolonytweaks_hasPrev = true;

        if (mariccolonytweaks_gaveUp)
        {
            if (now - mariccolonytweaks_lastForceMs >= GIVEUP_RETRY_MS)
            {
                mariccolonytweaks_gaveUp = false;
                mariccolonytweaks_forceCount = 0;
                mariccolonytweaks_stalledSinceMs = now;
            }
            return;
        }

        final int maxRetries = MaricColonyTweaks.MAX_RETRIES.get();
        if (mariccolonytweaks_forceCount >= maxRetries)
        {
            mariccolonytweaks_gaveUp = true;
            mariccolonytweaks_lastForceMs = now;
            return;
        }

        if (now - mariccolonytweaks_stalledSinceMs >= MaricColonyTweaks.STUCK_MS.get())
        {
            mariccolonytweaks_forceCount++;
            mariccolonytweaks_stalledSinceMs = now;
            mariccolonytweaks_lastForceMs = now;
            cir.setReturnValue(true);
        }
    }
}
