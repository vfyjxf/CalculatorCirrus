package dev.vfyjxf.cirrus.api.recipe.storage;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cloudlib.util.Checks;

/**
 * Immutable transfer request used for runtime storage simulation and execution.
 *
 * <p>A request always targets exactly one semantic shape:
 * <ul>
 *     <li>an exact {@link ContentResource}, typically for output storage or already-resolved stock</li>
 *     <li>an {@link InputContent}, typically for execution-profile direct supply that must keep tag/list semantics</li>
 * </ul>
 *
 * <p>The old nullable dual-field shape made call sites hard to read and easy to misuse.
 * This request now models that choice explicitly through {@link Target}.
 */
public record StorageTransferRequest(Target target, long amount) {

    public StorageTransferRequest {
        Checks.checkNotNull(target, "target");
        Checks.checkArgument(amount > 0, "amount must be positive");
    }

    public static StorageTransferRequest forResource(ContentResource resource, long amount) {
        return new StorageTransferRequest(new Target.Resource(resource), amount);
    }

    public static StorageTransferRequest forInput(InputContent input, long amount) {
        return new StorageTransferRequest(new Target.Input(input), amount);
    }

    public boolean matches(ContentResource candidate) {
        return target.matches(candidate);
    }

    public boolean targetsExactResource() {
        return target instanceof Target.Resource;
    }

    /**
     * Returns the exact resource target.
     *
     * @throws IllegalStateException when this request targets an input semantic instead
     */
    public ContentResource exactResource() {
        return switch (target) {
            case Target.Resource resource -> resource.resource();
            case Target.Input ignored -> throw new IllegalStateException("request does not target an exact resource");
        };
    }

    /**
     * The semantic match target for this request.
     */
    public sealed interface Target permits Target.Resource, Target.Input {

        boolean matches(ContentResource candidate);

        /**
         * Exact-resource transfer. Use this when the producer/consumer is already fully resolved.
         */
        record Resource(ContentResource resource) implements Target {
            public Resource {
                Checks.checkNotNull(resource, "resource");
            }

            @Override
            public boolean matches(ContentResource candidate) {
                return candidate.matchesResource(resource);
            }
        }

        /**
         * Input-semantic transfer. Use this when execution must preserve tags, lists, or other unresolved input semantics.
         */
        record Input(InputContent input) implements Target {
            public Input {
                Checks.checkNotNull(input, "input");
            }

            @Override
            public boolean matches(ContentResource candidate) {
                return input.satisfy(candidate);
            }
        }
    }
}
