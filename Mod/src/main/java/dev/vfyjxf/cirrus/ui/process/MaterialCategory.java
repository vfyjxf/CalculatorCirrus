package dev.vfyjxf.cirrus.ui.process;

import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Objects;

public record MaterialCategory(Namespace id, Component label, int color) {

    public MaterialCategory {
        id = id == null ? CirrusNamespaces.ofInternal("unknown") : id;
        label = label == null ? Component.literal(id.path()) : label;
    }

    public String name() {
        return id.path().toUpperCase(Locale.ROOT);
    }

    public String displayText() {
        return label.getString();
    }

    public boolean is(Namespace id) {
        return Objects.equals(this.id, id);
    }
}
