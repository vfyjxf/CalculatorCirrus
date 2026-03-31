package dev.vfyjxf.cirrus.api.recipe.database;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public record CustomRecipeDatabase(
    String id,
    String name,
    List<DatabaseRecipeRef> recipes,
    List<Namespace> sources,
    List<Namespace> disabledSources
) {
    public static final String builtinId = "builtin";
    public static final String builtinName = "Builtin";

    public static final Codec<CustomRecipeDatabase> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.fieldOf("id").forGetter(CustomRecipeDatabase::id),
        Codec.STRING.fieldOf("name").forGetter(CustomRecipeDatabase::name),
        DatabaseRecipeRef.CODEC.listOf().fieldOf("recipes").forGetter(CustomRecipeDatabase::recipes),
        Namespace.codec.listOf().fieldOf("sources").forGetter(CustomRecipeDatabase::sources),
        Namespace.codec.listOf().fieldOf("disabled_sources").forGetter(CustomRecipeDatabase::disabledSources)
    ).apply(instance, CustomRecipeDatabase::new));

    public CustomRecipeDatabase {
        Checks.checkNotNull(id, "id");
        Checks.checkNotNull(name, "name");
        Checks.checkNotNull(recipes, "recipes");
        Checks.checkNotNull(sources, "sources");
        Checks.checkNotNull(disabledSources, "disabledSources");
        id = id.isBlank() ? builtinId : id;
        name = name.isBlank() ? id : name;
        recipes = List.copyOf(new LinkedHashSet<>(recipes));
        sources = normalizeNamespaces(sources);
        LinkedHashSet<Namespace> disabled = new LinkedHashSet<>(normalizeNamespaces(disabledSources));
        disabled.retainAll(referencedSources(recipes, sources));
        disabledSources = List.copyOf(disabled);
    }

    public static CustomRecipeDatabase builtinDatabase() {
        return new CustomRecipeDatabase(builtinId, builtinName, List.of(), List.of(), List.of());
    }

    public boolean builtin() {
        return builtinId.equals(id);
    }

    public DatabaseDefinition definition(int priority) {
        return new DatabaseDefinition("custom:" + id, name, DatabaseKind.custom, true, priority, enabledSources());
    }

    public String definitionId() {
        return "custom:" + id;
    }

    public List<Namespace> enabledSources() {
        ArrayList<Namespace> out = new ArrayList<>();
        for (Namespace source : sources) {
            if (!disabledSources.contains(source)) {
                out.add(source);
            }
        }
        return List.copyOf(out);
    }

    public boolean hasSource(Namespace source) {
        return source != null && referencedSources().contains(source);
    }

    public boolean sourceEnabled(Namespace source) {
        return sources.contains(source) && !disabledSources.contains(source);
    }

    public boolean sourceDisabled(Namespace source) {
        return disabledSources.contains(source);
    }

    public boolean recipeEnabled(DatabaseRecipeRef ref) {
        return ref != null && !sourceDisabled(ref.system());
    }

    public boolean contains(DatabaseRecipeRef ref) {
        return recipes.contains(ref);
    }

    public CustomRecipeDatabase withRecipe(DatabaseRecipeRef ref) {
        ArrayList<DatabaseRecipeRef> next = new ArrayList<>(recipes);
        if (!next.contains(ref)) {
            next.add(ref);
        }
        return new CustomRecipeDatabase(id, name, next, sources, disabledSources);
    }

    public CustomRecipeDatabase withoutRecipe(DatabaseRecipeRef ref) {
        ArrayList<DatabaseRecipeRef> next = new ArrayList<>(recipes);
        next.remove(ref);
        return new CustomRecipeDatabase(id, name, next, sources, disabledSources);
    }

    public CustomRecipeDatabase withRecipeSelected(DatabaseRecipeRef ref, boolean selected) {
        return selected ? withRecipe(ref) : withoutRecipe(ref);
    }

    public CustomRecipeDatabase withSourceEnabled(Namespace source, boolean enabled) {
        Checks.checkNotNull(source, "source");
        ArrayList<Namespace> nextSources = new ArrayList<>(sources);
        boolean explicitlyReferenced = recipes.stream().anyMatch(ref -> source.equals(ref.system()));
        if (!nextSources.contains(source) && (enabled || !explicitlyReferenced)) {
            nextSources.add(source);
        }
        ArrayList<Namespace> nextDisabled = new ArrayList<>(disabledSources);
        if (enabled) {
            nextDisabled.remove(source);
        } else if (!nextDisabled.contains(source)) {
            nextDisabled.add(source);
        }
        return new CustomRecipeDatabase(id, name, recipes, nextSources, nextDisabled);
    }

    public CustomRecipeDatabase withoutSource(Namespace source) {
        Checks.checkNotNull(source, "source");
        ArrayList<Namespace> nextSources = new ArrayList<>(sources);
        nextSources.remove(source);
        ArrayList<Namespace> nextDisabled = new ArrayList<>(disabledSources);
        nextDisabled.remove(source);
        return new CustomRecipeDatabase(id, name, recipes, nextSources, nextDisabled);
    }

    private static List<Namespace> normalizeNamespaces(List<Namespace> namespaces) {
        LinkedHashSet<Namespace> out = new LinkedHashSet<>();
        for (Namespace namespace : namespaces) {
            if (namespace != null) {
                out.add(namespace);
            }
        }
        return List.copyOf(out);
    }

    private List<Namespace> referencedSources() {
        return referencedSources(recipes, sources);
    }

    private static List<Namespace> referencedSources(List<DatabaseRecipeRef> recipes, List<Namespace> sources) {
        LinkedHashSet<Namespace> out = new LinkedHashSet<>(sources);
        for (DatabaseRecipeRef recipe : recipes) {
            if (recipe != null && recipe.system() != null) {
                out.add(recipe.system());
            }
        }
        return List.copyOf(out);
    }
}
