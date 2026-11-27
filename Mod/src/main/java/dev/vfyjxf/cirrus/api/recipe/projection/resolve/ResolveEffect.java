package dev.vfyjxf.cirrus.api.recipe.projection.resolve;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContext;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.list.MutableList;

/**
 * Effect step executed by {@link ResolvePolicy} after atomic clause transformers.
 */
public interface ResolveEffect {

    //region factory

    static ResolveEffect of(Namespace id, ApplyFunction applier) {
        return of(id, 0, ResolveScope.both, applier);
    }

    static ResolveEffect of(Namespace id, int order, ResolveScope scope, ApplyFunction applier) {
        Checks.checkNotNull(id, "id");
        Checks.checkNotNull(scope, "scope");
        Checks.checkNotNull(applier, "applier");
        return new ResolveEffect() {
            @Override
            public Namespace id() {
                return id;
            }

            @Override
            public ResolveScope scope() {
                return scope;
            }

            @Override
            public int order() {
                return order;
            }

            @Override
            public void apply(
                ExecutionContext context,
                MutableList<InputContent> inputs,
                MutableList<OutputContent> outputs
            ) {
                applier.apply(context, inputs, outputs);
            }
        };
    }

    //endregion

    Namespace id();

    default ResolveScope scope() {
        return ResolveScope.both;
    }

    /**
     * Stable order key for extra ordering.
     */
    default int order() {
        return 0;
    }

    void apply(ExecutionContext context, MutableList<InputContent> inputs, MutableList<OutputContent> outputs);

    interface ApplyFunction {
        void apply(ExecutionContext context, MutableList<InputContent> inputs, MutableList<OutputContent> outputs);
    }
}
