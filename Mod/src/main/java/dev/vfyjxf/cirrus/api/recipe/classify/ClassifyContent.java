package dev.vfyjxf.cirrus.api.recipe.classify;

import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * A plain-data view of one recipe input or output slot, used for
 * classification. Deliberately free of any Minecraft types so the same
 * structure can be built from live recipe graph nodes or from exported JSON
 * data (offline analysis harness).
 *
 * <p>Id strings use the {@code namespace:path} registry form for items and
 * fluids; other content types fall back to their stack-type string form.
 *
 * @param kind   the structural kind of the content
 * @param ids    flattened concrete content ids (for {@code tag} these are the
 *               tag's member ids; for {@code any}/{@code typedAny} the variant
 *               ids; for {@code value}/{@code complex}/{@code empty} usually empty)
 * @param tagId  the referenced tag id ({@code namespace:path}), only for
 *               {@link Kind#tag}; otherwise {@code null}
 * @param amount demanded/produced amount (best effort)
 * @param chance chance in [0,1]; 1 means guaranteed
 */
public record ClassifyContent(Kind kind, Set<String> ids, @Nullable String tagId, double amount, double chance) {

    public enum Kind {
        stack, tag, any, typedAny, value, complex, empty
    }

    public ClassifyContent {
        ids = ids == null ? Set.of() : Set.copyOf(ids);
    }

    public static ClassifyContent stack(String id, double amount, double chance) {
        return new ClassifyContent(Kind.stack, Set.of(id), null, amount, chance);
    }

    public static ClassifyContent tag(String tagId, Set<String> members, double amount, double chance) {
        return new ClassifyContent(Kind.tag, members, tagId, amount, chance);
    }

    /** Returns {@code true} when this slot carries at least one concrete id. */
    public boolean hasIds() {
        return !ids.isEmpty();
    }
}
