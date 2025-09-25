package dev.vfyjxf.calculatorcirrus.api.content.render;

import dev.vfyjxf.calculatorcirrus.api.content.value.ContentValue;

public interface ContentRenderer<T> {

    void render(T content, int width, int height, float delta);

    default void render(ContentValue<T> content, int width, int height, float delta) {
        render(content.value(), width, height, delta);
    }

}
