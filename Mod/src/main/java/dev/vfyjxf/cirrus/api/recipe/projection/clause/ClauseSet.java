package dev.vfyjxf.cirrus.api.recipe.projection.clause;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MutableMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ClauseSet {

    public static ClauseSet create() {
        return new ClauseSet(Maps.immutable.empty());
    }

    public static ClauseSet create(MutableMap<Namespace, @Nullable RecipeClause> prototype) {
        var immutable = prototype.reject((k, v) -> v == null).toImmutable();
        return new ClauseSet(immutable);
    }

    public static ClauseSet ofImmutable(ImmutableMap<Namespace, RecipeClause> prototype) {
        ImmutableMap<Namespace, RecipeClause> reject = prototype.reject((k, v) -> v == null);
        return new ClauseSet(reject);
    }

    private final ImmutableMap<Namespace, @NotNull RecipeClause> prototype;
    private final MutableMap<Namespace, @Nullable RecipeClause> patches = Maps.mutable.empty();

    private ClauseSet(ImmutableMap<Namespace, RecipeClause> prototype) {
        this.prototype = prototype;
    }

    //region clause

    public ClauseSet addClause(RecipeClause clause) {
        Checks.checkNotNull(clause, "clause");
        patches.put(clause.id(), clause);
        return this;
    }

    public ClauseSet removeClause(ClauseType clauseType) {
        Checks.checkNotNull(clauseType, "clauseType");
        patches.put(clauseType.id(), null);
        return this;
    }

    public ClauseSet removeClause(Namespace clauseId) {
        Checks.checkNotNull(clauseId, "clauseId");
        patches.put(clauseId, null);
        return this;
    }

    public boolean hasClause(Namespace clauseId) {
        Checks.checkNotNull(clauseId, "clauseId");
        return patches.get(clauseId) != null || (!patches.containsKey(clauseId) && prototype.containsKey(clauseId));
    }

    public boolean hasClause(ClauseType clauseType) {
        Checks.checkNotNull(clauseType, "clauseType");
        return hasClause(clauseType.id());
    }

    public boolean hasClause(RecipeClause clause) {
        Checks.checkNotNull(clause, "clause");
        return hasClause(clause.id());
    }

    public RecipeClause getClause(Namespace clauseId) {
        RecipeClause ret = patches.get(clauseId);
        if (ret != null) return ret;
        if (patches.containsKey(clauseId)) {
            throw new IllegalArgumentException("No recipe clause found with id: " + clauseId);
        }
        return prototype.get(clauseId);
    }

    public RecipeClause getClause(ClauseType clauseType) {
        Checks.checkNotNull(clauseType, "clauseType");
        return getClause(clauseType.id());
    }

    @Contract(" -> new")
    public ClauseSet merge() {
        MutableMap<Namespace, RecipeClause> merged = this.prototype.toMap();
        patches.forEachKeyValue((k, v) -> {
            if (v == null) merged.remove(k);
            else merged.put(k, v);
        });
        return new ClauseSet(merged.toImmutable());
    }

    public void reset() {
        patches.clear();
    }

    public RichIterable<RecipeClause> allClauses() {
        if (patches.isEmpty()) return prototype;
        MutableMap<Namespace, RecipeClause> ret = prototype.toMap();
        patches.forEachKeyValue((k, v) -> {
            if (v == null) ret.remove(k);
            else ret.put(k, v);
        });
        return ret;
    }

    //endregion

    @Override
    public String toString() {
        return "[" +
               "prototype=" + (prototype.isEmpty() ? "empty" : prototype) +
               ", patches=" + (patches.isEmpty() ? "empty" : patches) +
               ']';
    }
}
