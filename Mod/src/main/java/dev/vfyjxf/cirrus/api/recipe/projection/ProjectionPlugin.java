package dev.vfyjxf.cirrus.api.recipe.projection;

import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.resources.ResourceLocation;

public record ProjectionPlugin(
    Namespace id,
    ExecutionContextKey<Boolean> enabledKey,
    String labelKey,
    String descriptionKey
) {
    public static final ProjectionPlugin builtin = ProjectionPlugin.of(
        CirrusNamespaces.ofMod("projection/builtin"),
        "cirrus.ui.calculator.database.plugin.builtin",
        "cirrus.ui.calculator.database.plugin.builtin.desc",
        true
    );

    public ProjectionPlugin {
        Checks.checkNotNull(id, "id");
        Checks.checkNotNull(enabledKey, "enabledKey");
        Checks.checkNotNull(labelKey, "labelKey");
        Checks.checkNotNull(descriptionKey, "descriptionKey");
    }

    public static ProjectionPlugin of(Namespace id, String labelKey, String descriptionKey, boolean enabledByDefault) {
        Checks.checkNotNull(id, "id");
        return new ProjectionPlugin(
            id,
            ExecutionContextKey.create(
                ResourceLocation.fromNamespaceAndPath(id.root(), id.path() + "/enabled"),
                Boolean.class,
                enabledByDefault
            ),
            labelKey,
            descriptionKey
        );
    }

    public boolean enabled(ExecutionContext context) {
        return context.get(enabledKey);
    }
}
