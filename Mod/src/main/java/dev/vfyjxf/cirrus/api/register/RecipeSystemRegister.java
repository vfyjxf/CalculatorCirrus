package dev.vfyjxf.cirrus.api.register;

import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.extension.RecipeSubsystem;
import dev.vfyjxf.cirrus.api.recipe.system.extension.SubsystemOrder;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface RecipeSystemRegister {

    void registerSystem(RecipeSystem<?> system);

    default <R> void registerSubsystem(Namespace systemId, Namespace subsystemId, RecipeSubsystem<R> subsystem) {
        registerSubsystem(systemId, subsystemId, subsystem, SubsystemOrder.standard());
    }

    <R> void registerSubsystem(
        Namespace systemId,
        Namespace subsystemId,
        RecipeSubsystem<R> subsystem,
        SubsystemOrder order
    );

}
