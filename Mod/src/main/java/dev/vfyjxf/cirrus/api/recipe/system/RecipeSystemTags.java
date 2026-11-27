package dev.vfyjxf.cirrus.api.recipe.system;

import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.Namespace;

/**
 * Standard 3-axis Cirrus {@link RecipeSystem} tag constants:
 * Process Domain × Data Provenance × Operational Trait.
 */
public final class RecipeSystemTags {

    //region Process Domain
    public static final Namespace crafting = CirrusNamespaces.ofMod("system/crafting");
    public static final Namespace cooking = CirrusNamespaces.ofMod("system/cooking");
    public static final Namespace cutting = CirrusNamespaces.ofMod("system/cutting");
    public static final Namespace smithing = CirrusNamespaces.ofMod("system/smithing");
    public static final Namespace brewing = CirrusNamespaces.ofMod("system/brewing");
    public static final Namespace composting = CirrusNamespaces.ofMod("system/composting");
    public static final Namespace fueling = CirrusNamespaces.ofMod("system/fueling");
    public static final Namespace anvil = CirrusNamespaces.ofMod("system/anvil");
    public static final Namespace grindstone = CirrusNamespaces.ofMod("system/grindstone");
    public static final Namespace oreProcessing = CirrusNamespaces.ofMod("system/ore_processing");
    public static final Namespace mechanical = CirrusNamespaces.ofMod("system/mechanical");
    public static final Namespace chemical = CirrusNamespaces.ofMod("system/chemical");
    public static final Namespace assembly = CirrusNamespaces.ofMod("system/assembly");
    public static final Namespace magicRitual = CirrusNamespaces.ofMod("system/magic_ritual");
    public static final Namespace resourceGen = CirrusNamespaces.ofMod("system/resource_gen");
    //endregion

    //region Provenance & Fidelity
    public static final Namespace vanilla = CirrusNamespaces.ofMod("system/vanilla");
    public static final Namespace modNative = CirrusNamespaces.ofMod("system/mod_native");
    public static final Namespace jeiFallback = CirrusNamespaces.ofMod("system/jei_fallback");
    public static final Namespace displayOnly = CirrusNamespaces.ofMod("system/display_only");
    public static final Namespace mirrorDuplicate = CirrusNamespaces.ofMod("system/mirror_duplicate");
    //endregion

    //region Operational Trait
    public static final Namespace handOperable = CirrusNamespaces.ofMod("system/hand_operable");
    public static final Namespace automatable = CirrusNamespaces.ofMod("system/automatable");
    public static final Namespace heavyMultiblock = CirrusNamespaces.ofMod("system/heavy_multiblock");
    public static final Namespace worldInteraction = CirrusNamespaces.ofMod("system/world_interaction");
    public static final Namespace optIn = CirrusNamespaces.ofMod("system/opt_in");
    //endregion

    private RecipeSystemTags() {}
}
