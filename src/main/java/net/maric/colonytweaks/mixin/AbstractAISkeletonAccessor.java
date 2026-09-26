package net.maric.colonytweaks.mixin;

import com.minecolonies.api.entity.citizen.AbstractEntityCitizen;
import com.minecolonies.core.entity.ai.workers.AbstractAISkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@code AbstractAISkeleton.worker}, which is declared on the superclass rather than
 * on the concrete worker AI. Mixin cannot {@code @Shadow} an inherited field, so this
 * accessor is the supported way to read it from a mixin targeting a subclass.
 *
 * <p>{@code @Accessor} has no {@code require} attribute in the Mixin version NeoForge
 * 21.1.249 ships, so this mixin's survivability rests on
 * {@code injectors.defaultRequire = 0} and {@code "required": false} in
 * mariccolonytweaks.mixins.json, plus the target check in ColonyTweaksMixinPlugin.
 */
@Mixin(AbstractAISkeleton.class)
public interface AbstractAISkeletonAccessor
{
    @Accessor("worker")
    AbstractEntityCitizen mariccolonytweaks$getWorker();
}
