package dev.vfyjxf.cirrus.api.recipe.projection.context;

import dev.vfyjxf.cirrus.api.recipe.projection.ExecutionContextKey;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public record ChoiceProjectionContextOption<T>(
    Namespace id,
    ProjectionPlugin plugin,
    ExecutionContextKey<T> key,
    String labelKey,
    String descriptionKey,
    List<ProjectionContextChoice<T>> choices
) implements ProjectionContextOption<T> {
    public ChoiceProjectionContextOption {
        Checks.checkNotNull(id, "id");
        Checks.checkNotNull(plugin, "plugin");
        Checks.checkNotNull(key, "key");
        Checks.checkNotNull(labelKey, "labelKey");
        Checks.checkNotNull(descriptionKey, "descriptionKey");
        Checks.checkNotNull(choices, "choices");
        Checks.checkArgument(!choices.isEmpty(), "choices cannot be empty");
        choices = List.copyOf(choices);
    }

    @Override
    public T normalize(@Nullable Object value) {
        for (ProjectionContextChoice<T> choice : choices) {
            if (Objects.equals(choice.value(), value)) {
                return choice.value();
            }
        }
        T fallback = defaultValue();
        for (ProjectionContextChoice<T> choice : choices) {
            if (Objects.equals(choice.value(), fallback)) {
                return choice.value();
            }
        }
        return choices.getFirst().value();
    }
}
