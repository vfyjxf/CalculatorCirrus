package dev.vfyjxf.cirrus.api.recipe.database;

public record RecipeDatabaseResult(RecipeDatabaseRecord record, int matchScore, int databasePriority, int matchOrder) {}
