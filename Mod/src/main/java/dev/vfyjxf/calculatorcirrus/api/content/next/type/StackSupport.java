package dev.vfyjxf.calculatorcirrus.api.content.next.type;

import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import net.minecraft.core.component.DataComponentPatch;

public interface StackSupport<T, S> {

    boolean isEmpty(S stack);

    long maxAmount();

    S makeStack(T identity, long amount, DataComponentPatch componentPatch);

    default S makeStack(T identity, DataComponentPatch componentPatch) {return makeStack(identity, 1, componentPatch);}

    default S makeStack(T identity) {return makeStack(identity, 1, DataComponentPatch.EMPTY);}

    T getIdentity(S stack);

    long mergeDiff(S stack1, S stack2, MatchStrategy strategy);

}
