package dev.vfyjxf.cirrus.api.recipe.projection.clause;

import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.eclipse.collections.api.RichIterable;
import org.jetbrains.annotations.Range;

public record ClausedContent<T extends RecipeContent>(
    T content,
    ClauseSet clauses
) {
    public static <T extends RecipeContent> ClausedContent<T> from(T content) {
        return new ClausedContent<>(content, ClauseSet.create());
    }

    public static <T extends RecipeContent> ClausedContent<T> from(T content, ClauseSet clauses) {
        return new ClausedContent<>(content, clauses);
    }

    public boolean isEmpty() {
        return content.isEmpty();
    }

    public boolean notEmpty() {
        return content.notEmpty();
    }

    @Range(from = 0, to = Long.MAX_VALUE)
    public long amount() {
        return content.amount();
    }

    @Range(from = 0, to = 1)
    public float chance() {
        return content.chance();
    }

    public boolean constantly() {
        return content.constantly();
    }

    public boolean mergeable(RecipeContent other) {
        return content.mergeable(other);
    }

    public boolean satisfy(ContentVariant actual) {
        return content.satisfy(actual);
    }

    //region clause

    public RichIterable<RecipeClause> allClauses() {
        return clauses.allClauses();
    }

    public RecipeClause getClause(ClauseType clauseType) {
        return clauses.getClause(clauseType);
    }

    public RecipeClause getClause(Namespace clauseId) {
        return clauses.getClause(clauseId);
    }

    public boolean hasClause(RecipeClause clause) {
        return clauses.hasClause(clause);
    }

    public boolean hasClause(ClauseType clauseType) {
        return clauses.hasClause(clauseType);
    }

    public boolean hasClause(Namespace clauseId) {
        return clauses.hasClause(clauseId);
    }

    public ClauseSet removeClause(Namespace clauseId) {
        return clauses.removeClause(clauseId);
    }

    public ClauseSet removeClause(ClauseType clauseType) {
        return clauses.removeClause(clauseType);
    }

    public ClauseSet addClause(RecipeClause clause) {
        return clauses.addClause(clause);
    }

    public void reset() {
        clauses.reset();
    }

    //endregion

    //region compatibility

    @Deprecated(forRemoval = false)
    public RichIterable<RecipeClause> allFacets() {
        return allClauses();
    }

    @Deprecated(forRemoval = false)
    public RecipeClause getFacet(ClauseType facetType) {
        return getClause(facetType);
    }

    @Deprecated(forRemoval = false)
    public RecipeClause getFacet(Namespace facetId) {
        return getClause(facetId);
    }

    @Deprecated(forRemoval = false)
    public ClauseSet removeFacet(Namespace facetId) {
        return removeClause(facetId);
    }

    @Deprecated(forRemoval = false)
    public ClauseSet removeFacet(ClauseType facetType) {
        return removeClause(facetType);
    }

    @Deprecated(forRemoval = false)
    public ClauseSet addFacet(RecipeClause facet) {
        return addClause(facet);
    }

    //endregion

    @Override
    public String toString() {
        return '[' +
               "content=" + content +
               ", clauses=" + clauses +
               ']';
    }
}
