package dev.vfyjxf.calculatorcirrus.recipe;

import dev.vfyjxf.calculatorcirrus.CirrusConstants;
import dev.vfyjxf.cloudlib.data.lang.LangBuilder;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import dev.vfyjxf.cloudlib.data.lang.LangProvider;


@LangProvider
public class RecipeLangs {

    private static final LangBuilder builder = LangBuilder.create(CirrusConstants.MOD_ID, "ui.data.recipes");

    public static final LangEntry defaultDatabaseName = builder.define("default", "Default Database");
}
