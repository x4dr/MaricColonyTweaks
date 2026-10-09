package net.maric.colonytweaks.mixin;

import com.minecolonies.core.colony.buildings.workerbuildings.BuildingFarmer;
import com.minecolonies.core.entity.ai.workers.production.agriculture.EntityAIWorkFarmer;
import net.maric.colonytweaks.MaricColonyTweaks;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.List;

/**
 * When "Use Bone Meal" is disabled on the farmer hut, strip Bone Meal out of the
 * fertilizer request. prepareForFarming builds a StackList request of
 * [compost, bone_meal]; removing bone meal here means the courier only ever
 * fetches Compost, so external storage (Create bridge etc.) keeps its supply.
 */
@Mixin(EntityAIWorkFarmer.class)
public abstract class FarmerBonemealRequestMixin
{
    @ModifyArg(
        method = "prepareForFarming",
        at = @At(
            value = "INVOKE",
            target = "Lcom/minecolonies/api/colony/requestsystem/requestable/StackList;<init>(Ljava/util/List;Ljava/lang/String;II)V"
        ),
        index = 0
    )
    private List<ItemStack> mariccolonytweaks$filterBonemeal(final List<ItemStack> stacks)
    {
        final BuildingFarmer building = ((EntityAIWorkFarmer) (Object) this).building;
        if (building != null)
        {
            final var setting = building.getSetting(MaricColonyTweaks.USE_BONEMEAL);
            if (setting != null && !setting.getValue())
            {
                stacks.removeIf(stack -> stack.is(Items.BONE_MEAL));
            }
        }
        return stacks;
    }
}
