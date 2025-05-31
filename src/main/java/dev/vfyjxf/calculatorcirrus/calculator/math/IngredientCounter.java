package dev.vfyjxf.calculatorcirrus.calculator.math;

public class IngredientCounter {

    private double amount = 0;
    private final Object identity;
    private final Object type;

    public IngredientCounter(Object identity, Object type) {
        this.identity = identity;
        this.type = type;
    }
}
