package dev.vfyjxf.cirrus.api.recipe.value;

import dev.vfyjxf.cirrus.content.type.BasicValueType;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;

public class TimeValueType extends BasicValueType<TimeValue> {

    public static final TimeValueType instance = new TimeValueType();

    private TimeValueType() {
        super(CirrusNamespaces.ofMod("time"));
    }

    @Override
    public long getValue(TimeValue content) {
        return content.time();
    }

    @Override
    public boolean matches(TimeValue fist, TimeValue second) {
        return fist.time() == second.time();
    }

    @Override
    public TimeValue fromValue(long value) {
        return new TimeValue(value);
    }

    @Override
    public TimeValue copy(TimeValue content) {
        return new TimeValue(content.time());
    }
}
