package dev.vfyjxf.cirrus.api.recipe.projection;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClauseSet;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClauseType;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.ClausedContent;
import dev.vfyjxf.cirrus.api.recipe.projection.clause.RecipeClause;
import dev.vfyjxf.cirrus.api.recipe.projection.schema.ProjectionSchema;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

import java.util.Collection;

public record RecipeProjection(
    RecipeEntry<?> recipe,
    ImmutableList<ClausedContent<InputContent>> inputs,
    ImmutableList<ClausedContent<OutputContent>> outputs,
    ProjectionSchema schema,
    ClauseSet clauses
) {

    //region factory

    public static <R> RecipeProjection create(
        RecipeSystem<R> system,
        R recipe,
        Collection<? extends InputContent> inputs,
        Collection<? extends OutputContent> outputs
    ) {
        Checks.checkNotNull(system, "system");
        Checks.checkNotNull(recipe, "recipe");
        return create(RecipeEntry.of(system, recipe), inputs, outputs);
    }

    public static <R> RecipeProjection create(
        RecipeEntry<R> entry,
        Collection<? extends InputContent> inputs,
        Collection<? extends OutputContent> outputs
    ) {
        Checks.checkNotNull(entry, "entry");
        Checks.checkNotNull(inputs, "inputs");
        Checks.checkNotNull(outputs, "outputs");
        Checks.checkArgument(!inputs.isEmpty(), "inputs cannot be empty");
        Checks.checkArgument(!outputs.isEmpty(), "outputs cannot be empty");

        return new RecipeProjection(
            entry,
            clausedInputs(inputs),
            clausedOutputs(outputs),
            ProjectionSchema.empty(),
            ClauseSet.create()
        );
    }

    private static ImmutableList<ClausedContent<InputContent>> clausedInputs(
        Collection<? extends InputContent> inputs
    ) {
        var out = Lists.mutable.<ClausedContent<InputContent>>ofInitialCapacity(inputs.size());
        for (InputContent input : inputs) {
            out.add(ClausedContent.from(input));
        }
        return out.toImmutable();
    }

    private static ImmutableList<ClausedContent<OutputContent>> clausedOutputs(
        Collection<? extends OutputContent> outputs
    ) {
        var out = Lists.mutable.<ClausedContent<OutputContent>>ofInitialCapacity(outputs.size());
        for (OutputContent output : outputs) {
            out.add(ClausedContent.from(output));
        }
        return out.toImmutable();
    }

    //endregion

    public RecipeProjection {
        Checks.checkNotNull(recipe, "recipe");
        Checks.checkNotNull(inputs, "inputs");
        Checks.checkNotNull(outputs, "outputs");
        Checks.checkNotNull(schema, "schema");
        Checks.checkNotNull(clauses, "clauses");
    }

    //region clause

    public RecipeClause getClause(Namespace clauseId) {
        return clauses.getClause(clauseId);
    }

    public ClauseSet addClause(RecipeClause clause) {
        return clauses.addClause(clause);
    }

    public ClauseSet removeClause(ClauseType clauseType) {
        return clauses.removeClause(clauseType);
    }

    public ClauseSet removeClause(Namespace clauseId) {
        return clauses.removeClause(clauseId);
    }

    public boolean hasClause(Namespace clauseId) {
        return clauses.hasClause(clauseId);
    }

    public boolean hasClause(ClauseType clauseType) {
        return clauses.hasClause(clauseType);
    }

    public boolean hasClause(RecipeClause clause) {
        return clauses.hasClause(clause);
    }

    public RecipeClause getClause(ClauseType clauseType) {
        return clauses.getClause(clauseType);
    }

    public void resetClauses() {
        clauses.reset();
    }

    public RichIterable<RecipeClause> allClauses() {
        return clauses.allClauses();
    }

    //endregion

    @Override
    public String toString() {
        return "RecipeProjection{" + "recipe=" + recipe + ", inputs=" + inputs + ", outputs=" + outputs + ", schema="
                + schema + ", clauses=" + clauses + '}';
    }
}
