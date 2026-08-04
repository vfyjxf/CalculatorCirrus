package dev.vfyjxf.cirrus.api.recipe.integration;

import dev.vfyjxf.cirrus.api.content.value.ContentValue;

public interface JeiValueTypeBridge<S, T> {

    ContentValue<T> resolve(S stack);

}
