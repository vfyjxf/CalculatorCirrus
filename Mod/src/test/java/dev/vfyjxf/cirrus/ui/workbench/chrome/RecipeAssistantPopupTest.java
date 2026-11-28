package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.MachineRequirement;
import dev.vfyjxf.cirrus.api.planning.graph.OutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeRole;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.planning.adapter.OfflineContentTypes;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeTestContext.coal;
import static dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeTestContext.demand;
import static dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeTestContext.ironIngot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeAssistantPopupTest {

    private static CompiledRecipe recipe(
        int compactId,
        String id,
        String system,
        @Nullable String machine,
        List<ComponentStackKey> ins,
        ComponentStackKey out
    ) {
        List<InputSlot> inputSlots = new ArrayList<>();
        for (int i = 0; i < ins.size(); i++) {
            inputSlots.add(new InputSlot(i, 1L, Optional.empty(), List.of(ins.get(i)), SlotMatchMode.identity));
        }
        MachineRequirement req = new MachineRequirement(
            Optional.empty(),
            machine == null ? List.of() : List.of(OfflineContentTypes.machine(machine)),
            false
        );
        return new CompiledRecipe(
            compactId,
            new CompiledRecipeKey(Namespace.parse(system), Optional.of(ResourceLocation.parse(id)), 0L),
            RecipeRole.standardProduction,
            inputSlots,
            List.of(),
            List.of(new OutputSlot(out, 1L)),
            req,
            Map.of()
        );
    }

    @Test
    void recipeAssistantFiltersSearchesSortsAndPinsRecipes() {
        ChromeTestContext ctx = new ChromeTestContext(1280f, 720f, List.of(demand(ironIngot, 8L)));
        ComponentStackKey ore = OfflineContentTypes.item("minecraft:iron_ore");
        ComponentStackKey block = OfflineContentTypes.item("minecraft:iron_block");
        ctx.graph.add(ironIngot, coal, ore, block);
        ctx.graph.addRecipe(
            recipe(
                1,
                "minecraft:iron_blast",
                "minecraft:blasting",
                "minecraft:blast_furnace",
                List.of(ore, coal),
                ironIngot
            )
        );
        ctx.graph.addRecipe(
            recipe(2, "minecraft:iron_smelt", "minecraft:smelting", "minecraft:furnace", List.of(ore, coal), ironIngot)
        );
        ctx.graph.addRecipe(recipe(3, "minecraft:iron_unpack", "minecraft:crafting", null, List.of(block), ironIngot));

        RecipeAssistantPopupWidget assistant = new RecipeAssistantPopupWidget(ctx);
        assertFalse(assistant.isOpen());

        assistant.openFor(ironIngot);
        assertTrue(assistant.isOpen());
        assertEquals(3, assistant.candidates().size());

        assistant.setFilterMode(RecipeAssistantPopupWidget.FilterMode.handFree);
        assertEquals(1, assistant.candidates().size());
        assertTrue(assistant.candidates().get(0).handCraft());

        assistant.setFilterMode(RecipeAssistantPopupWidget.FilterMode.machineOnly);
        assertEquals(2, assistant.candidates().size());

        assistant.setFilterMode(RecipeAssistantPopupWidget.FilterMode.all);
        assistant.setSearchQuery("blast");
        assertEquals(1, assistant.candidates().size());

        assistant.setSearchQuery("");
        assistant.setSortMode(RecipeAssistantPopupWidget.SortMode.lowWait);
        assertTrue(assistant.candidates().get(0).handCraft(), "hand craft with 0 wait should sort first");

        assertTrue(assistant.pinSelected());
        assertFalse(assistant.isOpen(), "pinning a recipe closes the assistant popup");
    }
}
