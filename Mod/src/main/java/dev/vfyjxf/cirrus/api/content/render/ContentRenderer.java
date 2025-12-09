package dev.vfyjxf.cirrus.api.content.render;

import dev.vfyjxf.cirrus.api.content.value.ContentObject;

public interface ContentRenderer<T> {

    void render(T content, int width, int height, float delta);

    default void render(ContentObject<T> content, int width, int height, float delta) {
        render(content.value(), width, height, delta);
    }

}
