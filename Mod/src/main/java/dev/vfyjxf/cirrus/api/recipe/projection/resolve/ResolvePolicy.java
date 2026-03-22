package dev.vfyjxf.cirrus.api.recipe.projection.resolve;

import dev.vfyjxf.cloudlib.api.util.MutableLists;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

public record ResolvePolicy(
    ImmutableList<ResolveGuard> guards,
    ImmutableList<ResolveEffect> effects,
    @Nullable Comparator<ResolveEffect> extraOrdering
) {

    public ResolvePolicy {
        Checks.checkNotNull(guards, "guards");
        Checks.checkNotNull(effects, "effects");
        guards.forEach(guard -> Checks.checkNotNull(guard, "guard"));
        effects.forEach(effect -> Checks.checkNotNull(effect, "effect"));
    }

    public ResolvePolicy(
        ImmutableList<ResolveGuard> guards,
        ImmutableList<ResolveEffect> effects
    ) {
        this(guards, effects, null);
    }

    //region effect order

    public ImmutableList<ResolveEffect> orderedEffects() {
        if (effects.isEmpty() || extraOrdering == null) return effects;

        MutableList<IndexedEffect> indexed = MutableLists.empty();
        for (int i = 0; i < effects.size(); i++) {
            indexed.add(new IndexedEffect(i, effects.get(i)));
        }

        indexed.sortThis((left, right) -> {
            int compared = extraOrdering.compare(left.effect(), right.effect());
            if (compared != 0) return compared;
            return Integer.compare(left.declaredIndex(), right.declaredIndex());
        });
        return indexed.collect(IndexedEffect::effect).toImmutable();
    }

    //endregion

    private record IndexedEffect(
        int declaredIndex,
        ResolveEffect effect
    ) {}
}
