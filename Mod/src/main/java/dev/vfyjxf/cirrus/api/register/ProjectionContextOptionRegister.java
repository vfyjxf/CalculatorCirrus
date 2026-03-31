package dev.vfyjxf.cirrus.api.register;

import dev.vfyjxf.cirrus.api.recipe.projection.context.ProjectionContextOption;

public interface ProjectionContextOptionRegister {

    void registerProjectionContextOption(ProjectionContextOption<?> option);
}
