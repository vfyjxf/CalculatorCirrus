package dev.vfyjxf.cirrus.api.recipe.projection;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Immutable GameplayTag-style hierarchical tag container attached to recipes and slots.
 *
 * <p>Hierarchical matching uses {@code /} path segments: a tag {@code mod:projection/fluid/container}
 * matches both exact {@code mod:projection/fluid/container} and parent prefix {@code mod:projection/fluid}.
 */
public final class ProjectionTags implements Iterable<Namespace> {

    private static final ProjectionTags empty = new ProjectionTags(Set.of());

    private final Set<Namespace> tags;

    private ProjectionTags(Set<Namespace> tags) {
        this.tags = tags.isEmpty() ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(tags));
    }

    public static ProjectionTags empty() {
        return empty;
    }

    public static ProjectionTags of(Namespace... tags) {
        Checks.checkNotNull(tags, "tags");
        if (tags.length == 0) {
            return empty;
        }
        LinkedHashSet<Namespace> set = new LinkedHashSet<>(tags.length);
        for (Namespace tag : tags) {
            set.add(Checks.checkNotNull(tag, "tag"));
        }
        return new ProjectionTags(set);
    }

    public static ProjectionTags ofAll(Collection<Namespace> tags) {
        Checks.checkNotNull(tags, "tags");
        if (tags.isEmpty()) {
            return empty;
        }
        LinkedHashSet<Namespace> set = new LinkedHashSet<>(tags.size());
        for (Namespace tag : tags) {
            set.add(Checks.checkNotNull(tag, "tag"));
        }
        return new ProjectionTags(set);
    }

    public boolean isEmpty() {
        return tags.isEmpty();
    }

    public int size() {
        return tags.size();
    }

    public boolean has(Namespace tag) {
        Checks.checkNotNull(tag, "tag");
        return tags.contains(tag);
    }

    /**
     * Hierarchical GAS-style tag match: returns true if this container holds {@code tag}
     * or any child tag whose namespace matches and path starts with {@code tag.path() + "/"}.
     */
    public boolean hasHierarchical(Namespace tag) {
        Checks.checkNotNull(tag, "tag");
        if (tags.contains(tag)) {
            return true;
        }
        String root = tag.root();
        String prefix = tag.path() + "/";
        for (Namespace owned : tags) {
            if (root.equals(owned.root()) && owned.path().startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAll(ProjectionTags required) {
        for (Namespace tag : required.tags) {
            if (!has(tag)) {
                return false;
            }
        }
        return true;
    }

    public boolean hasAny(ProjectionTags candidates) {
        for (Namespace tag : candidates.tags) {
            if (has(tag)) {
                return true;
            }
        }
        return false;
    }

    public boolean matches(TagQuery query) {
        Checks.checkNotNull(query, "query");
        return query.matches(this);
    }

    public ProjectionTags with(Namespace tag) {
        Checks.checkNotNull(tag, "tag");
        if (tags.contains(tag)) {
            return this;
        }
        LinkedHashSet<Namespace> next = new LinkedHashSet<>(tags);
        next.add(tag);
        return new ProjectionTags(next);
    }

    public ProjectionTags withAll(ProjectionTags other) {
        Checks.checkNotNull(other, "other");
        if (other.isEmpty()) {
            return this;
        }
        if (isEmpty()) {
            return other;
        }
        LinkedHashSet<Namespace> next = new LinkedHashSet<>(tags);
        next.addAll(other.tags);
        return new ProjectionTags(next);
    }

    public ProjectionTags without(Namespace tag) {
        Checks.checkNotNull(tag, "tag");
        if (!tags.contains(tag)) {
            return this;
        }
        if (tags.size() == 1) {
            return empty;
        }
        LinkedHashSet<Namespace> next = new LinkedHashSet<>(tags);
        next.remove(tag);
        return new ProjectionTags(next);
    }

    public Set<Namespace> values() {
        return tags;
    }

    @Override
    public Iterator<Namespace> iterator() {
        return tags.iterator();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ProjectionTags that && tags.equals(that.tags));
    }

    @Override
    public int hashCode() {
        return tags.hashCode();
    }

    @Override
    public String toString() {
        return "ProjectionTags" + tags;
    }
}
