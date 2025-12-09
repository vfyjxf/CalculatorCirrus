package dev.vfyjxf.cirrus.integration;

import dev.vfyjxf.cirrus.api.ClientRegisterContentEvent;
import dev.vfyjxf.cirrus.api.RegisterRecipeSystemEvent;

public interface ClientCirrusAdapter {

    default void registerContent(ClientRegisterContentEvent event) {

    }

    default void registerRecipeSystem(RegisterRecipeSystemEvent event) {

    }

}
