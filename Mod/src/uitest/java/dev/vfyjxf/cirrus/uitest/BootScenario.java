package dev.vfyjxf.cirrus.uitest;

import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import net.minecraft.client.Minecraft;

/**
 * The smallest boot check: the client exists and owns a window.
 */
@ClientScenario(name = "cirrus.boot", group = "smoke", tags = {"smoke"}, requiresWorld = false)
public final class BootScenario {

    public void define(ScenarioBuilder build) {
        build.check("the client window exists", () -> Minecraft.getInstance().getWindow().getWidth() > 0);
    }
}
