package dev.vfyjxf.calculatorcirrus.integration;


import dev.vfyjxf.calculatorcirrus.api.RegisterContentEvent;
import dev.vfyjxf.calculatorcirrus.api.RegisterRecipeSystemEvent;

public interface CirrusAdapter {

    void registerContent(RegisterContentEvent event);

    void registerRecipeSystem(RegisterRecipeSystemEvent event);
}
