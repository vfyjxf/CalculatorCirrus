package dev.vfyjxf.cirrus.api.recipe.projection.effect;

public sealed interface SchemaEffect extends ProjectionEffect permits ProjectionSchemaPut, InputSchemaPut, OutputSchemaPut {
}
