package dev.vfyjxf.calculatorcirrus.api.recipe.content;

import dev.vfyjxf.calculatorcirrus.api.content.type.ValueType;
import dev.vfyjxf.calculatorcirrus.content.type.BasicValueType;
import dev.vfyjxf.calculatorcirrus.util.CirrusLocations;

public final class EnergyContentType extends BasicValueType<EnergyValue> {

    public static final ValueType<EnergyValue> instance = new EnergyContentType();


    private EnergyContentType() {
        super(CirrusLocations.ofMod("energy"));
    }

    @Override
    public long getValue(EnergyValue content) {
        return content.energy();
    }

    @Override
    public EnergyValue fromValue(long value) {
        return new EnergyValue(value);
    }

    @Override
    public EnergyValue copy(EnergyValue content) {
        return new EnergyValue(content.energy());
    }
}
