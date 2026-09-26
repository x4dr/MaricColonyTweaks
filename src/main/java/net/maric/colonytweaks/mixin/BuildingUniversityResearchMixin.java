package net.maric.colonytweaks.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingUniversity;
import net.maric.colonytweaks.UniversityResearch;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * University research item requests (delivery side).
 *
 * <p>Runs the pending-research check on every colony tick (via
 * {@code BuildingUniversity.onColonyTick}, fired by the slow world tick) and persists the
 * pending marker inside the university's building NBT so it survives save/reload.
 */
@Mixin(BuildingUniversity.class)
public abstract class BuildingUniversityResearchMixin
{
    @Inject(method = "onColonyTick", at = @At("RETURN"), require = 0)
    private void mariccolonytweaks$checkPendingResearch(final IColony colony, final CallbackInfo ci)
    {
        UniversityResearch.onUniversityTick((BuildingUniversity) (Object) this, colony);
    }

    @Inject(
        method = "serializeNBT(Lnet/minecraft/core/HolderLookup$Provider;)Lnet/minecraft/nbt/CompoundTag;",
        at = @At("RETURN"),
        cancellable = true,
        require = 0)
    private void mariccolonytweaks$savePendingResearch(final HolderLookup.Provider provider, final CallbackInfoReturnable<CompoundTag> cir)
    {
        final CompoundTag tag = cir.getReturnValue();
        UniversityResearch.writePending((BuildingUniversity) (Object) this, tag);
        cir.setReturnValue(tag);
    }

    @Inject(
        method = "deserializeNBT(Lnet/minecraft/core/HolderLookup$Provider;Lnet/minecraft/nbt/CompoundTag;)V",
        at = @At("RETURN"),
        require = 0)
    private void mariccolonytweaks$loadPendingResearch(final HolderLookup.Provider provider, final CompoundTag tag, final CallbackInfo ci)
    {
        UniversityResearch.readPending((BuildingUniversity) (Object) this, tag);
    }
}
