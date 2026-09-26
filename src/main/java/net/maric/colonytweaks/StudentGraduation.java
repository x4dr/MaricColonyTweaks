package net.maric.colonytweaks;

import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.jobs.ModJobs;
import com.minecolonies.api.entity.citizen.Skill;
import com.minecolonies.api.util.MessageUtils;
import com.minecolonies.core.colony.buildings.modules.GuardBuildingModule;
import com.minecolonies.core.colony.buildings.modules.WorkerBuildingModule;
import com.minecolonies.core.colony.jobs.AbstractJobGuard;
import com.minecolonies.core.colony.jobs.JobUndertaker;
import com.minecolonies.core.util.BuildingUtils;

/**
 * Student graduation: when a Library Student reaches the study cap (the point where
 * studying no longer raises Intelligence), move them out of the Library so someone who
 * can still learn takes the slot.
 *
 * <p>Priority order:
 * <ol>
 *   <li>Take an open worker slot in a building that is set to auto-hire but has none to
 *       hire (no jobless citizens). The Library is left open.</li>
 *   <li>Otherwise swap jobs with the lowest-Intelligence employed citizen who is still
 *       under the cap. The graduated student takes their job; they become the Student.</li>
 * </ol>
 *
 * <p>Guards and Undertakers are never taken over, and never used as swap partners.
 */
public final class StudentGraduation
{
    private StudentGraduation()
    {
    }

    public static void onStudentModuleTick(final WorkerBuildingModule studentModule, final IColony colony)
    {
        if (!MaricColonyTweaks.ENABLE_STUDENT_SWAPS.get() || studentModule.getJobEntry() != ModJobs.student.get())
        {
            return;
        }

        for (final ICitizenData student : studentModule.getAssignedCitizen())
        {
            if (!isAtStudyCap(student, colony))
            {
                continue;
            }

            final WorkerBuildingModule openSlot = findOpenSlot(studentModule, colony);
            if (openSlot != null && moveStudent(studentModule, openSlot, student))
            {
                MessageUtils.format("mariccolonytweaks.student.graduated.slot",
                        student.getName(), openSlot.getJobDisplayName(), openSlot.getBuilding().getBuildingDisplayName())
                    .sendTo(colony)
                    .forAllPlayers();
                return;
            }

            final ICitizenData partner = findLowestIntelligencePartner(student, colony);
            if (partner == null)
            {
                continue;
            }
            final WorkerBuildingModule partnerModule = findWorkerModule(partner);
            if (partnerModule == null || !swapStudents(studentModule, partnerModule, student, partner))
            {
                continue;
            }
            MessageUtils.format("mariccolonytweaks.student.graduated.swap",
                    student.getName(), partner.getName(), partner.getName())
                .sendTo(colony)
                .forAllPlayers();
            return;
        }
    }

    /**
     * Same cap the game uses in {@code CitizenSkillHandler.tryLevelUpIntelligence}:
     * Intelligence stops gaining XP once it reaches {@code (int) happiness * 9}.
     */
    private static boolean isAtStudyCap(final ICitizenData citizen, final IColony colony)
    {
        final int cap = (int) citizen.getCitizenHappinessHandler().getHappiness(colony, citizen) * 9;
        return citizen.getCitizenSkillHandler().getLevel(Skill.Intelligence) >= cap;
    }

    /**
     * First worker slot in the colony that is open and would auto-hire if a jobless citizen
     * existed. Guards and Undertakers are excluded.
     */
    private static WorkerBuildingModule findOpenSlot(final WorkerBuildingModule studentModule, final IColony colony)
    {
        for (final IBuilding building : colony.getServerBuildingManager().getBuildings().values())
        {
            for (final WorkerBuildingModule module : building.getModules(WorkerBuildingModule.class))
            {
                if (module == studentModule
                        || module instanceof GuardBuildingModule
                        || module.getJobEntry() == ModJobs.undertaker.get()
                        || module.isFull()
                        || !BuildingUtils.canAutoHire(module.getBuilding(), module.getHiringMode(), module.getJobEntry()))
                {
                    continue;
                }
                return module;
            }
        }
        return null;
    }

    /**
     * Lowest-Intelligence adult who is employed, not a guard/undertaker/student, and still
     * below the study cap.
     */
    private static ICitizenData findLowestIntelligencePartner(final ICitizenData student, final IColony colony)
    {
        ICitizenData best = null;
        int bestIntelligence = Integer.MAX_VALUE;
        for (final ICitizenData candidate : colony.getCitizenManager().getCitizens())
        {
            if (candidate == student
                    || candidate.isChild()
                    || candidate.getWorkBuilding() == null
                    || candidate.getJob() == null
                    || candidate.getJob() instanceof AbstractJobGuard
                    || candidate.getJob() instanceof JobUndertaker
                    || candidate.getJob().getJobRegistryEntry() == ModJobs.student.get()
                    || isAtStudyCap(candidate, colony))
            {
                continue;
            }
            final int intelligence = candidate.getCitizenSkillHandler().getLevel(Skill.Intelligence);
            if (intelligence < bestIntelligence)
            {
                bestIntelligence = intelligence;
                best = candidate;
            }
        }
        return best;
    }

    private static WorkerBuildingModule findWorkerModule(final ICitizenData citizen)
    {
        final IBuilding building = citizen.getWorkBuilding();
        if (building == null)
        {
            return null;
        }
        for (final WorkerBuildingModule module : building.getModules(WorkerBuildingModule.class))
        {
            if (module.hasAssignedCitizen(citizen))
            {
                return module;
            }
        }
        return null;
    }

    private static boolean moveStudent(final WorkerBuildingModule studentModule, final WorkerBuildingModule target, final ICitizenData student)
    {
        if (!studentModule.removeCitizen(student))
        {
            return false;
        }
        if (target.assignCitizen(student))
        {
            return true;
        }
        studentModule.assignCitizen(student);
        return false;
    }

    private static boolean swapStudents(final WorkerBuildingModule studentModule, final WorkerBuildingModule partnerModule,
                                        final ICitizenData student, final ICitizenData partner)
    {
        if (!studentModule.removeCitizen(student) || !partnerModule.removeCitizen(partner))
        {
            studentModule.assignCitizen(student);
            partnerModule.assignCitizen(partner);
            return false;
        }

        final boolean studentMoved = partnerModule.assignCitizen(student);
        final boolean partnerMoved = studentModule.assignCitizen(partner);
        if (studentMoved && partnerMoved)
        {
            return true;
        }

        if (studentMoved)
        {
            partnerModule.removeCitizen(student);
        }
        if (partnerMoved)
        {
            studentModule.removeCitizen(partner);
        }
        studentModule.assignCitizen(student);
        partnerModule.assignCitizen(partner);
        return false;
    }
}
