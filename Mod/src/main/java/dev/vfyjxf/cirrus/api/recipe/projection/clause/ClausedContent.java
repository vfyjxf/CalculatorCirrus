package dev.vfyjxf.cirrus.api.recipe.projection.clause;

import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.ProjectionSchema;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.RichIterable;
import org.jetbrains.annotations.Range;

public record ClausedContent<T extends RecipeContent>(
    T content,
    ProjectionSchema schema,
    ClauseSet clauses
) {
    public static <T extends RecipeContent> ClausedContent<T> from(T content) {
        return new ClausedContent<>(content, ProjectionSchema.empty(), ClauseSet.create());
    }

    public static <T extends RecipeContent> ClausedContent<T> from(T content, ClauseSet clauses) {
        return new ClausedContent<>(content, ProjectionSchema.empty(), clauses);
    }

    public static <T extends RecipeContent> ClausedContent<T> from(T content, ProjectionSchema schema, ClauseSet clauses) {
        return new ClausedContent<>(content, schema, clauses);
    }

    public ClausedContent {
        Checks.checkNotNull(content, "content");
        Checks.checkNotNull(schema, "schema");
        Checks.checkNotNull(clauses, "clauses");
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

    @Override
    public String toString() {
        return '[' +
               "content=" + content +
               ", schema=" + schema +
               ", clauses=" + clauses +
               ']';
    }
}
