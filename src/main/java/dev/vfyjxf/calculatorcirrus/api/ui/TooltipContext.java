package dev.vfyjxf.calculatorcirrus.api.ui;

import dev.vfyjxf.calculatorcirrus.api.math.Point;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.client.ClientTooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public record TooltipContext(Point mousePos, @Nullable TooltipFlag flag) {

    public TooltipContext(Point mousePos, @Nullable TooltipFlag flag) {
        this.mousePos = Objects.requireNonNull(mousePos);
        this.flag = flag;
    }

    @Override
    public TooltipFlag flag() {
        if (flag == null) {
            return ClientTooltipFlag.of(
                    Minecraft.getInstance().options.advancedItemTooltips ?
                            TooltipFlag.Default.ADVANCED :
                            TooltipFlag.Default.NORMAL
            );
        } else return flag;
    }
}
