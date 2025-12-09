package dev.vfyjxf.cirrus.api.recipe.routing.evaluator;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.routing.RoutingContext;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.Namespace;

public non-sealed interface ContentEvaluator extends RoutingEvaluator {

    Namespace basic = CirrusNamespaces.ofMod("evaluator/basic");
    Namespace stock = CirrusNamespaces.ofMod("evaluator/stock");

    double getCost(ContentStack<?, ?> stack, RoutingContext context);

}
