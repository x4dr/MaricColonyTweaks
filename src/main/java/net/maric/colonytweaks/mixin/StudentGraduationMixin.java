package net.maric.colonytweaks.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import net.maric.colonytweaks.StudentGraduation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the worker-module colony tick (every 500 ticks / 25 s via
 * {@code AbstractBuilding.onColonyTick} from {@code Colony.worldTickSlow}) and lets the
 * Library's Student module hand off a capped-out student.
 *
 * Guard and WorkAtHome modules override onColonyTick and do not reach this injection; the
 * Student module is a plain WorkerBuildingModule, so it does.
 */
@Mixin(WorkerBuildingModule.class)
public abstract class StudentGraduationMixin
{
    @Inject(method = "onColonyTick", at = @At("RETURN"), require = 0)
    private void mariccolonytweaks$graduateStudents(final IColony colony, final CallbackInfo ci)
    {
        StudentGraduation.onStudentModuleTick((WorkerBuildingModule) (Object) this, colony);
    }
}
