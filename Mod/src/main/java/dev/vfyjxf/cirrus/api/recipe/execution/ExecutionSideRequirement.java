package dev.vfyjxf.cirrus.api.recipe.execution;

import org.jspecify.annotations.Nullable;

/**
 * Declares which runtime side can execute a translated interaction step.
 */
public enum ExecutionSideRequirement {
    environmentAgnostic(true, true, true), client(true, false, true), server(false, true, true), player(
        true,
        false,
        false
    );

    //region shared instances

    public static final ExecutionSideRequirement manual = player;
    public static final ExecutionSideRequirement clientSide = client;
    public static final ExecutionSideRequirement serverSide = server;
    public static final ExecutionSideRequirement manualPlayerAction = player;

    //endregion

    private final boolean allowsClientExecution;
    private final boolean allowsServerExecution;
    private final boolean onlyRunsAutomatically;

    ExecutionSideRequirement(
        boolean allowsClientExecution,
        boolean allowsServerExecution,
        boolean onlyRunsAutomatically
    ) {
        this.allowsClientExecution = allowsClientExecution;
        this.allowsServerExecution = allowsServerExecution;
        this.onlyRunsAutomatically = onlyRunsAutomatically;
    }

    public boolean allowsClientExecution() {
        return allowsClientExecution;
    }

    public boolean allowsServerExecution() {
        return allowsServerExecution;
    }

    public boolean onlyRunsAutomatically() {
        return onlyRunsAutomatically;
    }

    public boolean supports(@Nullable ExecutionSideRequirement required) {
        if (required == null || this == environmentAgnostic || required == environmentAgnostic) {
            return true;
        }
        return switch (this) {
            case client -> required == client || required == player;
            case server -> required == server;
            case player -> required == player;
            case environmentAgnostic -> true;
        };
    }
}
