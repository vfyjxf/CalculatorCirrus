package dev.vfyjxf.cirrus.api.recipe.system;

import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Standard Cirrus {@link RecipeSystem} tag constants:
 * Process Domain × Data Provenance × Operational Trait × Database Folder Hierarchy.
 *
 * <p>Any plugin can declare arbitrary nested Database explorer folders by tagging a system with
 * {@link #folder(String...)} (e.g. {@code calculator_cirrus:database/folder/jei/create} or
 * {@code mymod:database/folder/machines/ore_processing}).
 */
public final class RecipeSystemTags {

    public static final String databaseFolderPrefix = "database/folder/";

    public enum TagAxis {
        provenance,
        process,
        trait,
        folder,
        custom
    }

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
    public static final Namespace debug = CirrusNamespaces.ofMod("system/debug");
    //endregion

    //region Operational Trait
    public static final Namespace handOperable = CirrusNamespaces.ofMod("system/hand_operable");
    public static final Namespace automatable = CirrusNamespaces.ofMod("system/automatable");
    public static final Namespace heavyMultiblock = CirrusNamespaces.ofMod("system/heavy_multiblock");
    public static final Namespace worldInteraction = CirrusNamespaces.ofMod("system/world_interaction");
    public static final Namespace optIn = CirrusNamespaces.ofMod("system/opt_in");
    //endregion

    //region Technical Database Folder Hierarchy
    public static final Namespace folderMinecraft = folder("minecraft");
    public static final Namespace folderJei = folder("jei");
    public static final Namespace folderNative = folder("native");
    public static final Namespace folderDebug = folder("debug");

    /**
     * Builds a hierarchical database explorer folder tag under {@code calculator_cirrus:database/folder/...}.
     */
    public static Namespace folder(String... segments) {
        StringBuilder path = new StringBuilder(databaseFolderPrefix);
        boolean first = true;
        for (String raw : segments) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            for (String part : raw.split("/")) {
                String normalized = part.trim().toLowerCase(Locale.ROOT);
                if (normalized.isEmpty()) {
                    continue;
                }
                if (!first) {
                    path.append('/');
                }
                path.append(normalized);
                first = false;
            }
        }
        if (first) {
            throw new IllegalArgumentException("Folder tag requires at least one non-empty path segment");
        }
        return CirrusNamespaces.ofMod(path.toString());
    }

    public static boolean isFolderTag(@Nullable Namespace tag) {
        return tag != null && tag.path().startsWith(databaseFolderPrefix)
                && tag.path().length() > databaseFolderPrefix.length();
    }

    /**
     * Extracts normalized folder segments from any {@code <ns>:database/folder/<seg1>/<seg2>/...} tag.
     */
    public static @Nullable List<String> folderSegments(@Nullable Namespace tag) {
        if (tag == null || !isFolderTag(tag)) {
            return null;
        }
        String tail = tag.path().substring(databaseFolderPrefix.length());
        String[] rawParts = tail.split("/");
        List<String> out = new ArrayList<>(rawParts.length);
        for (String part : rawParts) {
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out.isEmpty() ? null : List.copyOf(out);
    }

    public static TagAxis tagAxis(Namespace tag) {
        if (isFolderTag(tag)) {
            return TagAxis.folder;
        }
        if (tag.equals(vanilla) || tag.equals(modNative) || tag.equals(jeiFallback)
                || tag.equals(displayOnly) || tag.equals(mirrorDuplicate) || tag.equals(debug)) {
            return TagAxis.provenance;
        }
        if (tag.equals(handOperable) || tag.equals(automatable) || tag.equals(heavyMultiblock)
                || tag.equals(worldInteraction) || tag.equals(optIn)) {
            return TagAxis.trait;
        }
        if (tag.equals(crafting) || tag.equals(cooking) || tag.equals(cutting) || tag.equals(smithing)
                || tag.equals(brewing) || tag.equals(composting) || tag.equals(fueling) || tag.equals(anvil)
                || tag.equals(grindstone) || tag.equals(oreProcessing) || tag.equals(mechanical)
                || tag.equals(chemical) || tag.equals(assembly) || tag.equals(magicRitual)
                || tag.equals(resourceGen)) {
            return TagAxis.process;
        }
        return TagAxis.custom;
    }
    //endregion

    private RecipeSystemTags() {}
}
