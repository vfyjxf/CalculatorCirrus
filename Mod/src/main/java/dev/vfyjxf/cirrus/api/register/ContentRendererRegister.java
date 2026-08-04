package dev.vfyjxf.cirrus.api.register;

import dev.vfyjxf.cirrus.api.content.render.ContentRenderer;
import dev.vfyjxf.cirrus.api.content.render.StackRenderer;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.NonExtendable
public interface ContentRendererRegister {

    <T> void registerRenderer(ContentType<T> contentType, ContentRenderer<T> renderer);

    <T, S> void registerRenderer(StackType<T, S> stackType, StackRenderer<T, S> renderer);

}
