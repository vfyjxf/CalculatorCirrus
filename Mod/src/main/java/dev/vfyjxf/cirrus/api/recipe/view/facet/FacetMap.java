package dev.vfyjxf.cirrus.api.recipe.view.facet;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MutableMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FacetMap {

    public static FacetMap create() {
        return new FacetMap(Maps.immutable.empty());
    }

    public static FacetMap create(MutableMap<Namespace, @Nullable RecipeFacet> protype) {
        var immutable = protype.reject((k, v) -> v == null).toImmutable();
        return new FacetMap(immutable);
    }

    public static FacetMap ofImmutable(ImmutableMap<Namespace, RecipeFacet> protype) {
        ImmutableMap<Namespace, RecipeFacet> reject = protype.reject((k, v) -> v == null);
        return new FacetMap(reject);
    }

    private final ImmutableMap<Namespace, @NotNull RecipeFacet> protype;
    private final MutableMap<Namespace, @Nullable RecipeFacet> patches = Maps.mutable.empty();

    private FacetMap(ImmutableMap<Namespace, RecipeFacet> protype) {
        this.protype = protype;
    }

    public FacetMap addFacet(RecipeFacet facet) {
        Checks.checkNotNull(facet, "facet");
        patches.put(facet.id(), facet);
        return this;
    }

    public FacetMap removeFacet(RecipeFacetType facetType) {
        Checks.checkNotNull(facetType, "facetType");
        patches.put(facetType.id(), null);
        return this;
    }

    public FacetMap removeFacet(Namespace facetId) {
        Checks.checkNotNull(facetId, "facetId");
        patches.put(facetId, null);
        return this;
    }

    public boolean hasFacet(Namespace facetId) {
        Checks.checkNotNull(facetId, "facetId");
        return patches.get(facetId) != null || protype.contains(facetId);
    }

    public boolean hasFacet(RecipeFacetType facetType) {
        Checks.checkNotNull(facetType, "facetType");
        return hasFacet(facetType.id());
    }

    public boolean hasFacet(RecipeFacet facet) {
        Checks.checkNotNull(facet, "facet");
        return hasFacet(facet.id());
    }

    public RecipeFacet getFacet(Namespace facetId) {
        RecipeFacet ret = patches.get(facetId);
        if (ret != null) return ret;
        if (patches.containsKey(facetId)) {
            throw new IllegalArgumentException("No recipe facet found with id: " + facetId);
        }
        return protype.get(facetId);
    }

    public RecipeFacet getFacet(RecipeFacetType facetType) {
        Checks.checkNotNull(facetType, "facetType");
        return getFacet(facetType.id());
    }

    @Contract(" -> new")
    public FacetMap merge() {
        MutableMap<Namespace, RecipeFacet> protype = this.protype.toMap();
        patches.forEachKeyValue((k, v) -> {
            if (v == null) protype.remove(k);
            else protype.put(k, v);
        });
        return new FacetMap(protype.toImmutable());
    }

    public void reset() {
        patches.clear();
    }

    public RichIterable<RecipeFacet> allFacets() {
        if (patches.isEmpty()) return protype;
        MutableMap<Namespace, RecipeFacet> ret = protype.toMap();
        patches.forEachKeyValue((k, v) -> {
            if (v == null) ret.remove(k);
            else ret.put(k, v);
        });
        return ret;
    }

    @Override
    public String toString() {
        return "[" +
               "protype=" + (protype.isEmpty() ? "empty" : protype) +
               ", patches=" + (patches.isEmpty() ? "empty" : patches) +
               ']';
    }
}
