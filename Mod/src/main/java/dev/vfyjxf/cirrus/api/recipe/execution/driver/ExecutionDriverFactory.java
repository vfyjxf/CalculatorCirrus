package dev.vfyjxf.cirrus.api.recipe.execution.driver;

@FunctionalInterface
public interface ExecutionDriverFactory {

    ExecutionDriver create(DriverLookup lookup);
}
