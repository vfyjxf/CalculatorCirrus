package dev.vfyjxf.cirrus.api.recipe.routing.heuristic;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Immutable planner hints for one normalized recipe node.
 */
public record RecipeRoutingHints(
    RecipeUsePolicy usePolicy,
    int penalty,
    Set<Namespace> roles,
    @Nullable String reasonKey
) {
    private static final RecipeRoutingHints empty = new RecipeRoutingHints(
        RecipeUsePolicy.normal,
        0,
        Set.of(),
        null
    );

    public RecipeRoutingHints {
        Checks.checkNotNull(usePolicy, "usePolicy");
        Checks.checkNotNull(roles, "roles");
        roles = Set.copyOf(roles);
        if (penalty < 0) {
            penalty = 0;
        }
    }

    public static RecipeRoutingHints empty() {
        return empty;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean isEmpty() {
        return usePolicy == RecipeUsePolicy.normal
            && penalty == 0
            && roles.isEmpty()
            && (reasonKey == null || reasonKey.isBlank());
    }

    public RecipeRoutingHints merge(RecipeRoutingHints other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        LinkedHashSet<Namespace> mergedRoles = new LinkedHashSet<>(roles);
        mergedRoles.addAll(other.roles);
        return new RecipeRoutingHints(
            strictest(usePolicy, other.usePolicy),
            safeAdd(penalty, other.penalty),
            mergedRoles,
            reasonKey != null && !reasonKey.isBlank() ? reasonKey : other.reasonKey
        );
    }

    private static RecipeUsePolicy strictest(RecipeUsePolicy left, RecipeUsePolicy right) {
        return severity(left) >= severity(right) ? left : right;
    }

    private static int severity(RecipeUsePolicy policy) {
        return switch (policy) {
            case normal -> 0;
            case discouraged -> 1;
            case lastResort -> 2;
            case disabledByDefault -> 3;
            case targetOnly -> 4;
        };
    }

    private static int safeAdd(int left, int right) {
        long sum = (long) left + right;
        return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    public static final class Builder {
        private RecipeUsePolicy usePolicy = RecipeUsePolicy.normal;
        private int penalty;
        private final LinkedHashSet<Namespace> roles = new LinkedHashSet<>();
        private String reasonKey;

        private Builder() {
        }

        public Builder policy(RecipeUsePolicy policy) {
            this.usePolicy = Checks.checkNotNull(policy, "policy");
            return this;
        }

        public Builder discouraged() {
            return policy(RecipeUsePolicy.discouraged);
        }

        public Builder lastResort() {
            return policy(RecipeUsePolicy.lastResort);
        }

        public Builder disabledByDefault() {
            return policy(RecipeUsePolicy.disabledByDefault);
        }

        public Builder targetOnly() {
            return policy(RecipeUsePolicy.targetOnly);
        }

        public Builder recycling() {
            roles.add(Namespace.of("cirrus", "routing/role/recycling"));
            return this;
        }

        public Builder role(Namespace role) {
            roles.add(Checks.checkNotNull(role, "role"));
            return this;
        }

        public Builder penalty(int penalty) {
            this.penalty = Math.max(0, penalty);
            return this;
        }

        public Builder addPenalty(int penalty) {
            this.penalty = safeAdd(this.penalty, Math.max(0, penalty));
            return this;
        }

        public Builder reasonKey(String reasonKey) {
            this.reasonKey = reasonKey;
            return this;
        }

        public RecipeRoutingHints build() {
            return new RecipeRoutingHints(usePolicy, penalty, roles, reasonKey);
        }
    }

    public static RecipeRoutingHints canonical(RecipeRoutingHints hints) {
        if (hints == null || hints.isEmpty()) {
            return empty;
        }
        return hints;
    }
}
