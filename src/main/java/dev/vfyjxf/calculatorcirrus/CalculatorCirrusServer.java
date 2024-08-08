package dev.vfyjxf.calculatorcirrus;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(value = Constants.MOD_ID, dist = Dist.DEDICATED_SERVER)
public class CalculatorCirrusServer extends CalculatorCirrus{
    public CalculatorCirrusServer(IEventBus modEventBus, ModContainer modContainer) {
        super(modEventBus, modContainer);
    }
}
