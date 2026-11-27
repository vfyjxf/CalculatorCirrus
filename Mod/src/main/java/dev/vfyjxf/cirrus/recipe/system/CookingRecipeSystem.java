package dev.vfyjxf.cirrus.recipe.system;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;

import java.util.List;

public final class CookingRecipeSystem<T extends AbstractCookingRecipe> extends VanillaIngredientRecipeSystem<T> {

    public static final Namespace smeltingSystemId = Namespace.ofMc("smelting");
    public static final Namespace blastingSystemId = Namespace.ofMc("blasting");
    public static final Namespace smokingSystemId = Namespace.ofMc("smoking");
    public static final Namespace campfireCookingSystemId = Namespace.ofMc("campfire_cooking");

    private final Item defaultMachineItem;

    public static CookingRecipeSystem<SmeltingRecipe> smelting() {
        return new CookingRecipeSystem<>(smeltingSystemId, RecipeType.SMELTING, Items.FURNACE);
    }

    public static CookingRecipeSystem<BlastingRecipe> blasting() {
        return new CookingRecipeSystem<>(blastingSystemId, RecipeType.BLASTING, Items.BLAST_FURNACE);
    }

    public static CookingRecipeSystem<SmokingRecipe> smoking() {
        return new CookingRecipeSystem<>(smokingSystemId, RecipeType.SMOKING, Items.SMOKER);
    }

    public static CookingRecipeSystem<CampfireCookingRecipe> campfireCooking() {
        return new CookingRecipeSystem<>(campfireCookingSystemId, RecipeType.CAMPFIRE_COOKING, Items.CAMPFIRE);
    }

    private CookingRecipeSystem(Namespace systemId, RecipeType<T> recipeType, Item defaultMachineItem) {
        super(systemId, recipeType);
        this.defaultMachineItem = defaultMachineItem;
    }

    @Override
    protected List<InputContent> extraInputs(T recipe) {
        int ticks = recipe.getCookingTime();
        return ticks > 0 ? List.of(InputContent.fromTimeTick(ticks)) : List.of();
    }

    @Override
    public MachineEntry conventionMachine() {
        return MachineEntry.fromItemStack(new ItemStack(defaultMachineItem));
    }

    @Override
    public String toString() {
        return "CookingRecipeSystem{" + systemId() + '}';
    }
}
