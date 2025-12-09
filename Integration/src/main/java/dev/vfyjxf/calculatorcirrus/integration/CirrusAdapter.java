package dev.vfyjxf.cirrus.integration;


import dev.vfyjxf.cirrus.api.RegisterContentEvent;
import dev.vfyjxf.cirrus.api.RegisterRecipeSystemEvent;

public interface CirrusAdapter {

    void registerContent(RegisterContentEvent event);

    void registerRecipeSystem(RegisterRecipeSystemEvent event);
}
