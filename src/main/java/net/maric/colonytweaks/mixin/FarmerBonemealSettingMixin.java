package net.maric.colonytweaks.mixin;

import com.minecolonies.api.colony.buildings.modules.ISettingsModule;
import com.minecolonies.api.colony.buildings.modules.settings.ISetting;
import com.minecolonies.api.colony.buildings.modules.settings.ISettingKey;
import com.minecolonies.core.colony.buildings.modules.SettingsModule;
import com.minecolonies.core.colony.buildings.modules.settings.BoolSetting;
import com.minecolonies.core.colony.buildings.workerbuildings.BuildingFarmer;
import net.maric.colonytweaks.MaricColonyTweaks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Registers MaricColonyTweaks's "Use Bone Meal" setting on the farmer hut.
 *
 * The farmer's SettingsModule is built as
 * {@code new SettingsModule().with(FERTILIZE, ...).with(RECIPE_MODE, ...)} inside
 * BuildingModules. Tapping SettingsModule.with lets us append the extra setting
 * exactly when the farmer's FERTILIZE key passes through, so it is stored, saved
 * and synced like every other hut setting and shows up in the Settings tab.
 */
@Mixin(SettingsModule.class)
public abstract class FarmerBonemealSettingMixin
{
    @Inject(method = "with", at = @At("RETURN"), require = 0)
    private void mariccolonytweaks$addBonemealSetting(final ISettingKey<?> key, final ISetting<?> setting,
                                              final CallbackInfoReturnable<ISettingsModule> cir)
    {
        if (key == BuildingFarmer.FERTILIZE)
        {
            cir.getReturnValue().with(MaricColonyTweaks.USE_BONEMEAL, new BoolSetting(true));
        }
    }
}
