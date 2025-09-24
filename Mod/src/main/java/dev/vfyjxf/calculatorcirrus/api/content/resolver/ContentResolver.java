package dev.vfyjxf.calculatorcirrus.api.content.resolver;

import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;

public interface ContentResolver<T> {

    ContentType<T> contentType();

}
