package dev.vfyjxf.calculatorcirrus.api.recipe.content;

import dev.vfyjxf.calculatorcirrus.content.type.BasicValueType;
import dev.vfyjxf.calculatorcirrus.util.CirrusLocations;

public class TimeValueType extends BasicValueType<TimeValue> {

    public static final TimeValueType instance = new TimeValueType();

    private TimeValueType() {
        super(CirrusLocations.ofMod("time"));
    }

    @Override
    public long getValue(TimeValue content) {
        return content.time();
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
