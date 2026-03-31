package dev.vfyjxf.cirrus.recipe;

import dev.vfyjxf.cirrus.CirrusConstants;
import dev.vfyjxf.cloudlib.data.lang.LangBuilder;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import dev.vfyjxf.cloudlib.data.lang.LangProvider;


@LangProvider
public class RecipeLangs {

    private static final LangBuilder builder = LangBuilder.create(CirrusConstants.modId, "ui.data.recipes");

    public static final LangEntry defaultDatabaseName = builder.define("default", "Default Database");
}
