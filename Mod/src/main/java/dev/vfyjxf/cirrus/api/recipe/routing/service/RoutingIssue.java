package dev.vfyjxf.cirrus.api.recipe.routing.service;

import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.map.ImmutableMap;
import org.jetbrains.annotations.Nullable;

/**
 * Machine-readable issue attached to a {@link RoutingOutcome}.
 *
 * <p>Each issue identifies a specific problem that occurred during routing.
 * Issues carry a {@link RoutingFailureCode} for programmatic handling, a
 * translatable {@code messageKey} for user-facing display, and optional
 * anchor references to the plan action where the problem occurred.
 *
 * @param issueId           unique identifier within the outcome's issue list
 * @param code              the failure classification code
 * @param severity          how severe this issue is
 * @param blocking          whether this issue prevented further progress
 * @param messageKey        i18n message key for user-facing display
 * @param messageArgs       substitution arguments for the message template
 * @param anchorActionId    optional action ID where the issue occurred
 * @param anchorActionIndex optional action index where the issue occurred
 * @param details           additional machine-readable detail key-value pairs
 */
public record RoutingIssue(
    String issueId,
    RoutingFailureCode code,
    Severity severity,
    boolean blocking,
    String messageKey,
    ImmutableMap<String, String> messageArgs,
    RoutingIssuePayload payload,
    @Nullable Integer anchorActionId,
    @Nullable Integer anchorActionIndex,
    ImmutableMap<String, String> details
) {
    public RoutingIssue {
        Checks.checkNotNull(issueId, "issueId");
        Checks.checkNotNull(code, "code");
        Checks.checkNotNull(severity, "severity");
        Checks.checkNotNull(messageKey, "messageKey");
        Checks.checkNotNull(messageArgs, "messageArgs");
        Checks.checkNotNull(payload, "payload");
        Checks.checkNotNull(details, "details");
    }

    public Object[] translationArgs() {
        return payload.translationArgs();
    }

    private Object[] messageArgsOrdered() {
        if (messageArgs.isEmpty()) {
            return new Object[0];
        }
        return messageArgs.keyValuesView()
            .toList()
            .sortThisBy(pair -> pair.getOne())
            .collect(pair -> pair.getTwo())
            .toArray(new Object[messageArgs.size()]);
    }

    /** Severity level of a routing issue. */
    public enum Severity {
        /** Informational observation; does not affect plan validity. */
        info,
        /** Non-blocking warning; the plan may still be usable. */
        warning,
        /** Blocking error; this issue prevented successful planning. */
        error
    }
}

