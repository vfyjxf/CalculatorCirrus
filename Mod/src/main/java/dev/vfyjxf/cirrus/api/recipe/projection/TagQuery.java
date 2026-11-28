package dev.vfyjxf.cirrus.api.recipe.projection;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

import java.util.Arrays;
import java.util.Collection;

/**
 * Declarative GAS-style tag matcher supporting required-all, required-any, and forbidden tags
 * with optional hierarchical {@code /} prefix matching.
 */
public record TagQuery(
    ProjectionTags requireAll,
    ProjectionTags requireAny,
    ProjectionTags forbidAny,
    boolean hierarchical
) {
    private static final TagQuery any = new TagQuery(
        ProjectionTags.empty(),
        ProjectionTags.empty(),
        ProjectionTags.empty(),
        true
    );

    public TagQuery {
        Checks.checkNotNull(requireAll, "requireAll");
        Checks.checkNotNull(requireAny, "requireAny");
        Checks.checkNotNull(forbidAny, "forbidAny");
    }

    public static TagQuery any() {
        return any;
    }

    public static TagQuery requireAll(Namespace... tags) {
        return new TagQuery(ProjectionTags.of(tags), ProjectionTags.empty(), ProjectionTags.empty(), true);
    }

    public static TagQuery requireAny(Namespace... tags) {
        return new TagQuery(ProjectionTags.empty(), ProjectionTags.of(tags), ProjectionTags.empty(), true);
    }

    public static TagQuery forbid(Namespace... tags) {
        return new TagQuery(ProjectionTags.empty(), ProjectionTags.empty(), ProjectionTags.of(tags), true);
    }

    public TagQuery andRequireAll(Namespace... tags) {
        return new TagQuery(requireAll.withAll(ProjectionTags.of(tags)), requireAny, forbidAny, hierarchical);
    }

    public TagQuery andRequireAll(Collection<Namespace> tags) {
        return new TagQuery(requireAll.withAll(ProjectionTags.ofAll(tags)), requireAny, forbidAny, hierarchical);
    }

    public TagQuery andRequireAny(Namespace... tags) {
        return new TagQuery(requireAll, requireAny.withAll(ProjectionTags.of(tags)), forbidAny, hierarchical);
    }

    public TagQuery andForbid(Namespace... tags) {
        return new TagQuery(requireAll, requireAny, forbidAny.withAll(ProjectionTags.of(tags)), hierarchical);
    }

    public TagQuery exact() {
        return new TagQuery(requireAll, requireAny, forbidAny, false);
    }

    public boolean isUnconstrained() {
        return requireAll.isEmpty() && requireAny.isEmpty() && forbidAny.isEmpty();
    }

    public boolean matches(ProjectionTags target) {
        Checks.checkNotNull(target, "target");
        for (Namespace required : requireAll) {
            if (!has(target, required)) {
                return false;
            }
        }
        if (!requireAny.isEmpty()) {
            boolean foundAny = false;
            for (Namespace candidate : requireAny) {
                if (has(target, candidate)) {
                    foundAny = true;
                    break;
                }
            }
            if (!foundAny) {
                return false;
            }
        }
        for (Namespace forbidden : forbidAny) {
            if (has(target, forbidden)) {
                return false;
            }
        }
        return true;
    }

    private boolean has(ProjectionTags target, Namespace tag) {
        return hierarchical ? target.hasHierarchical(tag) : target.has(tag);
    }

    @SuppressWarnings("unused")
    private static ProjectionTags ofAll(Namespace... tags) {
        return ProjectionTags.ofAll(Arrays.asList(tags));
    }
}
