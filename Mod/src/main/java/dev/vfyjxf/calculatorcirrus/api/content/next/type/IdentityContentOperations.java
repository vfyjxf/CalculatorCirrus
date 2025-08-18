package dev.vfyjxf.calculatorcirrus.api.content.next.type;

public record IdentityContentOperations<T>(
        HashSupport<T> hash,
        RegistrySupport<T> registry,
        ComponentSupport<T> component,
        CopySupport<T> copy,
        MatchSupport<T> match,
        SaveSupport<T> save,
        CheatSupport<T> cheat
) {
}
