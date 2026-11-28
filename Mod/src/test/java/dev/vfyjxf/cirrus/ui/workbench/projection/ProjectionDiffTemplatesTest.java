package dev.vfyjxf.cirrus.ui.workbench.projection;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.complex.DurabilityContent;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotAddress;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotChannel;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.SlotDiff;
import dev.vfyjxf.cirrus.content.ContentApiImpl;
import dev.vfyjxf.cirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.cirrus.internal.InternalPlugin;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class ProjectionDiffTemplatesTest {

    @Test
    void resolvesSpecializedTemplatesForValueFluidMovedTagAndStackContents() {
        ContentApiImpl previous = ContentApiImpl.instance();
        attachContentApi();
        try {
            // 1. Energy ValueContent -> valueMeter template with FE units & comparative gauge bar
            ProjectionDiffTemplate.DiffInput energyInput = new ProjectionDiffTemplate.DiffInput(
                SlotAddress.input(1),
                SlotDiff.DiffStatus.adjusted,
                InputContent.fromEnergy(1000L),
                InputContent.fromEnergy(16_000L),
                null,
                "\u00d716"
            );
            ProjectionDiffTemplate energyTpl = ProjectionDiffTemplates.resolve(energyInput);
            assertEquals(ProjectionDiffTemplate.Kind.valueMeter, energyTpl.kind());
            assertTrue(energyTpl.extraBodyHeight(energyInput) > 0);
            String energyDetail = energyTpl.formatDetail(energyInput);
            assertTrue(energyDetail.contains("FE"));
            assertFalse(energyDetail.contains("calculator_cirrus:energy="));
            assertEquals("\u00d716", energyTpl.formatBadge(energyInput));

            // 2. Time ValueContent -> valueMeter template with tick/second units & negative percent badge
            ProjectionDiffTemplate.DiffInput timeInput = new ProjectionDiffTemplate.DiffInput(
                SlotAddress.output(3),
                SlotDiff.DiffStatus.adjusted,
                OutputContent.fromTimeTick(200),
                OutputContent.fromTimeTick(50),
                null,
                "-150"
            );
            ProjectionDiffTemplate timeTpl = ProjectionDiffTemplates.resolve(timeInput);
            assertEquals(ProjectionDiffTemplate.Kind.valueMeter, timeTpl.kind());
            String timeDetail = timeTpl.formatDetail(timeInput);
            assertTrue(timeDetail.contains("200t"));
            assertTrue(timeDetail.contains("50t"));
            assertFalse(timeDetail.contains("calculator_cirrus:time="));
            assertEquals("-75%", timeTpl.formatBadge(timeInput));

            // 3. Fluid transition -> fluid template with mB units
            ProjectionDiffTemplate.DiffInput fluidInput = new ProjectionDiffTemplate.DiffInput(
                SlotAddress.inputGrid(1, 1, 3),
                SlotDiff.DiffStatus.transformed,
                InputContent.fromItemStack(new ItemStack(Items.WATER_BUCKET)),
                InputContent.fromFluidStack(new FluidStack(Fluids.WATER, 1000)),
                null,
                "\u21c4"
            );
            ProjectionDiffTemplate fluidTpl = ProjectionDiffTemplates.resolve(fluidInput);
            assertEquals(ProjectionDiffTemplate.Kind.fluid, fluidTpl.kind());
            assertTrue(fluidTpl.formatDetail(fluidInput).contains("1000 mB"));

            // 4. Grid move -> moved template with origin->target badge and compact visual width
            SlotAddress from = SlotAddress.inputGrid(0, 0, 3);
            SlotAddress to = SlotAddress.inputGrid(2, 2, 3);
            RecipeContent iron = InputContent.fromItemStack(new ItemStack(Items.IRON_INGOT));
            ProjectionDiffTemplate.DiffInput movedInput = new ProjectionDiffTemplate.DiffInput(
                to,
                SlotDiff.DiffStatus.moved,
                iron,
                iron,
                from,
                "\u2192"
            );
            ProjectionDiffTemplate movedTpl = ProjectionDiffTemplates.resolve(movedInput);
            assertEquals(ProjectionDiffTemplate.Kind.moved, movedTpl.kind());
            assertEquals("(0,0)\u2192(2,2)", movedTpl.formatBadge(movedInput));
            assertTrue(movedTpl.visualColumnWidth(movedInput) < ProjectionDiffTemplates.stackSlot.visualColumnWidth(movedInput));

            // 5. Tag ingredient -> tagOrAlternatives template
            ProjectionDiffTemplate.DiffInput tagInput = new ProjectionDiffTemplate.DiffInput(
                SlotAddress.input(0),
                SlotDiff.DiffStatus.adjusted,
                InputContent.fromItemTag(ItemTags.PLANKS, 2),
                InputContent.fromItemTag(ItemTags.PLANKS, 6),
                null,
                "\u00d73"
            );
            ProjectionDiffTemplate tagTpl = ProjectionDiffTemplates.resolve(tagInput);
            assertEquals(ProjectionDiffTemplate.Kind.tagOrAlternatives, tagTpl.kind());
            assertTrue(tagTpl.formatHeader(tagInput).contains("Tag"));
            assertTrue(tagTpl.describeSingle(InputContent.fromItemTag(ItemTags.PLANKS, 2)).startsWith("#"));

            // 6. Durability requirement -> durability template with -N dur badge, uses/tool detail, and life bar
            ItemStack freshPick = new ItemStack(Items.IRON_PICKAXE);
            InputContent durOne = DurabilityContent.fromItemStack(freshPick, 1);
            ProjectionDiffTemplate.DiffInput transformInput = new ProjectionDiffTemplate.DiffInput(
                SlotAddress.inputGrid(0, 0, 3),
                SlotDiff.DiffStatus.transformed,
                InputContent.fromItemStack(freshPick),
                durOne,
                null,
                "\u21c4"
            );
            ProjectionDiffTemplate wearTpl = ProjectionDiffTemplates.resolve(transformInput);
            assertEquals(ProjectionDiffTemplate.Kind.durability, wearTpl.kind());
            assertTrue(wearTpl.extraBodyHeight(transformInput) > 0);
            assertEquals("-1 dur", wearTpl.formatBadge(transformInput));
            assertTrue(wearTpl.formatDetail(transformInput).contains("-1 dur"));
            assertTrue(wearTpl.formatDetail(transformInput).contains("250 uses/tool"));
            assertTrue(ProjectionDiffTemplates.describeContent(durOne).contains("-1 dur"));

            InputContent durFour = DurabilityContent.fromItemStack(freshPick, 4);
            ProjectionDiffTemplate.DiffInput adjustInput = new ProjectionDiffTemplate.DiffInput(
                SlotAddress.inputGrid(0, 2, 3),
                SlotDiff.DiffStatus.adjusted,
                durOne,
                durFour,
                null,
                "-4d"
            );
            ProjectionDiffTemplate adjustTpl = ProjectionDiffTemplates.resolve(adjustInput);
            assertEquals(ProjectionDiffTemplate.Kind.durability, adjustTpl.kind());
            assertEquals("-4 dur", adjustTpl.formatBadge(adjustInput));
            assertTrue(adjustTpl.formatDetail(adjustInput).contains("-1 dur \u2192 -4 dur"));

            // 7. Standard item stack -> stackSlot template
            ProjectionDiffTemplate.DiffInput stackInput = new ProjectionDiffTemplate.DiffInput(
                SlotAddress.indexed(SlotChannel.input, 0),
                SlotDiff.DiffStatus.adjusted,
                InputContent.fromItemStack(new ItemStack(Items.COPPER_INGOT, 2)),
                InputContent.fromItemStack(new ItemStack(Items.COPPER_INGOT, 6)),
                null,
                "\u00d73"
            );
            ProjectionDiffTemplate stackTpl = ProjectionDiffTemplates.resolve(stackInput);
            assertEquals(ProjectionDiffTemplate.Kind.stackSlot, stackTpl.kind());
        } finally {
            if (previous != null) {
                ContentApiImpl.attach(previous);
            }
        }
    }

    private static void attachContentApi() {
        ContentRegistryBuilder builder = new ContentRegistryBuilder();
        InternalPlugin internal = new InternalPlugin();
        internal.registerContentType(builder);
        internal.registerContentVariant(builder);
        internal.registerContentResolver(builder);
        ContentApiImpl.attach(new ContentApiImpl(builder));
    }
}
