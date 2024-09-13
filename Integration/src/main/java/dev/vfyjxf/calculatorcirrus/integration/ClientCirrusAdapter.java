package dev.vfyjxf.calculatorcirrus.integration;

import dev.vfyjxf.calculatorcirrus.api.ClientRegisterContentEvent;
import dev.vfyjxf.calculatorcirrus.api.RegisterRecipeSystemEvent;

public interface ClientCirrusAdapter {

   default void registerContent(ClientRegisterContentEvent event){

    }

    default void registerRecipeSystem(RegisterRecipeSystemEvent event) {

    }

}
