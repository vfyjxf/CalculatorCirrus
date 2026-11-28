package dev.vfyjxf.cirrus.api.register;

import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionEffect;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface ProjectionEffectRegister {

    void registerProjectionEffect(ProjectionEffect effect);
}
