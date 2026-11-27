package dev.vfyjxf.cirrus.api.recipe;

import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContextKey;
import dev.vfyjxf.cirrus.api.recipe.projection.context.CalculatorProjectionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.context.DatabaseProjectionContext;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ProjectionContextKeys;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ProjectionContextScope;
import dev.vfyjxf.cirrus.api.recipe.projection.context.ProjectionContexts;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectionContextLayerTest {

    private static final ExecutionContextKey<String> valueKey = ExecutionContextKey.create(
        ResourceLocation.fromNamespaceAndPath("test", "projection/context/layer/value"),
        String.class,
        "default"
    );

    @Test
    void calculatorProjectionContextReadsCalculatorDatabaseAndGlobalLayers() {
        ExecutionContext global = ProjectionContexts.global().set(valueKey, "global");
        ExecutionContext database = ProjectionContexts
                .database(global, new DatabaseProjectionContext("workspace", "Workspace", false));
        ExecutionContext calculator = ProjectionContexts
                .calculator(database, new CalculatorProjectionContext(null, 16, 42L));

        assertEquals(ProjectionContextScope.calculator, calculator.get(ProjectionContextKeys.scope));
        assertEquals("workspace", calculator.get(ProjectionContextKeys.database).databaseId());
        assertEquals(16, calculator.get(ProjectionContextKeys.calculator).times());
        assertEquals("global", calculator.get(valueKey));
        assertTrue(calculator.has(valueKey));
        assertFalse(calculator.hasLocal(valueKey));
    }

    @Test
    void localContextValuesOverrideParentWithoutMutatingParentLayers() {
        ExecutionContext global = ProjectionContexts.global().set(valueKey, "global");
        ExecutionContext database = ProjectionContexts
                .database(global, new DatabaseProjectionContext("db", "Database", false)).set(valueKey, "database");
        ExecutionContext calculator = ProjectionContexts
                .calculator(database, new CalculatorProjectionContext(null, 1, 0L)).set(valueKey, "calculator");

        assertEquals("calculator", calculator.get(valueKey));
        calculator.remove(valueKey);
        assertEquals("database", calculator.get(valueKey));
        database.clear();
        assertEquals("global", calculator.get(valueKey));
    }
}
