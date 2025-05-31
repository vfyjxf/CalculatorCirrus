package dev.vfyjxf.calculatorcirrus.api.recipe;

/**
 * The modifier for an ingredient.
 * <p>
 * It describes some special properties of an ingredient.
 * <p>
 * Such the tool damaging recipe or the fluid bucket recipe,they return something after crafting.
 * <p>
 * NOTE:Every implementing class should have a singleton.
 */
public interface IngredientModifier {

    void applyModifier();

}
