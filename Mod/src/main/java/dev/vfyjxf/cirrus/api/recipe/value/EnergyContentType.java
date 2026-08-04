package dev.vfyjxf.cirrus.api.recipe.value;

import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.content.type.BasicValueType;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;

public final class EnergyContentType extends BasicValueType<EnergyValue> {

    public static final ValueType<EnergyValue> instance = new EnergyContentType();


    private EnergyContentType() {
        super(CirrusNamespaces.ofMod("energy"));
    }

    @Override
    public long getValue(EnergyValue content) {
        return content.energy();
    }

    @Override
    public boolean matches(EnergyValue fist, EnergyValue second) {
        return fist.energy() == second.energy();
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
