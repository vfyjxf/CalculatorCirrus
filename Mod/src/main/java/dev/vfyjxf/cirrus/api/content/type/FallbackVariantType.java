package dev.vfyjxf.cirrus.api.content.type;

import dev.vfyjxf.cloudlib.api.util.Namespace;

record FallbackVariantType<T, S>(Namespace typeId, StackType<T, S> stackType) implements VariantType<T, S> {
    @Override
    public S copyWithIdentity(S stack) {
        return stackType.copy(stack);
    }

    @Override
    public boolean matchesVariantIdentity(S stack1, S stack2) {
        return true;
    }

    @Override
    public int hashIdentity(S stack) {
        return 0;
    }
}
