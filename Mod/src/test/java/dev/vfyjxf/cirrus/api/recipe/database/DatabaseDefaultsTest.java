package dev.vfyjxf.cirrus.api.recipe.database;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseDefaultsTest {
    @Test
    void defaultsDoNotImportSystemsAutomatically() {
        List<Namespace> systems = List.of(
            Namespace.of("core", "crafting"),
            Namespace.of("core", "smelting")
        );
        List<Namespace> jei = List.of(
            Namespace.of("jei", "category/smelting")
        );

        List<DatabaseDefinition> defs = DatabaseDefaults.generate(systems, jei);

        assertEquals(0, defs.size());
    }
}
