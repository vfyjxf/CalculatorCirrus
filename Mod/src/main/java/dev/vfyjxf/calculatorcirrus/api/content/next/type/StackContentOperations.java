package dev.vfyjxf.calculatorcirrus.api.content.next.type;

public record StackContentOperations<T, S>(
        IdentityContentOperations<T> identityOps,
        StackSupport<T, S> stack,
        ComponentSupport<S> component,
        MatchSupport<S> match,
        CopySupport<S> copy,
        SaveSupport<S> save,
        HashSupport<S> hash,
        NormalizeSupport<S> normalize,
        WildcardSupport<S> wildcard,
        CheatSupport<T> cheat
) {
}
