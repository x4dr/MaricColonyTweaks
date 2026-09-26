package net.maric.colonytweaks.mixin;

import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.api.util.Log;
import com.minecolonies.api.util.constant.TranslationConstants;
import com.minecolonies.core.entity.ai.workers.service.EntityAIWorkDeliveryman;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Names the building when a courier fails a delivery because a worker chest is full.
 *
 * MineColonies' generic "I can't deliver because of a full worker chest." message is used
 * when the destination has no assigned worker module — the Warehouse, but also the Town
 * Hall, PostBox or Stash. It passes the building's schematic name as a translation argument
 * wrapped in a Component.literal (" :<schematic>"), but the lang string has no %s
 * placeholder, so the name is silently dropped and the player cannot tell which chest is
 * full.
 *
 * Two redirects inside EntityAIWorkDeliveryman.deliver():
 *  - getSchematicName() is called exactly once, in that branch: log building + position to
 *    latest.log, and stash the display name / coordinates for the message below (the
 *    literal argument is evaluated before the translatable call, so order is guaranteed).
 *  - Component.translatableEscape(String, Object[]) is intercepted and, only for the
 *    generic chest-full key, the dropped argument is replaced with "<display name> at
 *    <x, y, z>" via a MaricColonyTweaks lang key that has the %s. Other keys (the named-worker
 *    variant) and the argless title call pass through untouched.
 */
@Mixin(EntityAIWorkDeliveryman.class)
public abstract class CourierFullChestLogMixin
{
    @Unique
    private String mariccolonytweaks$lastName = "";

    @Unique
    private String mariccolonytweaks$lastPos = "";

    @Redirect(
        method = "deliver",
        at = @At(
            value = "INVOKE",
            target = "Lcom/minecolonies/api/colony/buildings/IBuilding;getSchematicName()Ljava/lang/String;"),
        require = 0)
    private String mariccolonytweaks$logFullChest(final IBuilding building)
    {
        final AbstractEntityCitizen worker = ((AbstractAISkeletonAccessor) (Object) this).mariccolonytweaks$getWorker();
        Log.getLogger().warn("[MaricColonyTweaks] {} can't deliver: worker chest full at {} '{}' ({})",
            worker != null ? worker.getName().getString() : "Courier",
            building.getID(),
            building.getBuildingDisplayName(),
            building.getSchematicName());
        mariccolonytweaks$lastName = building.getBuildingDisplayName();
        mariccolonytweaks$lastPos = building.getID().toShortString();
        return building.getSchematicName();
    }

    @Redirect(
        method = "deliver",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/chat/Component;translatableEscape(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"),
        require = 0)
    private MutableComponent mariccolonytweaks$fixChestFullMessage(final String key, final Object[] args)
    {
        if (!TranslationConstants.COM_MINECOLONIES_COREMOD_JOB_DELIVERYMAN_CHESTFULL.equals(key))
        {
            return Component.translatableEscape(key, args);
        }

        // Argless call is the interaction's title line - leave it vanilla.
        if (args.length == 0)
        {
            return Component.translatableEscape(key);
        }

        final String at = mariccolonytweaks$lastPos.isEmpty() ? "" : " at " + mariccolonytweaks$lastPos;
        return Component.translatableEscape("mariccolonytweaks.delivery.workerchestfull",
            (mariccolonytweaks$lastName.isEmpty() ? fallbackName(args) : mariccolonytweaks$lastName) + at);
    }

    @Unique
    private static String fallbackName(final Object[] args)
    {
        if (args[0] instanceof final Component nameComponent)
        {
            String name = nameComponent.getString();
            if (name.startsWith(" :"))
            {
                name = name.substring(2);
            }
            return name;
        }
        return "an unknown building";
    }
}