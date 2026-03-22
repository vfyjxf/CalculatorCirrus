package dev.vfyjxf.cirrus.ui.calc.result;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.routing.Stock;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingBudget;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;

public final class ResultStepsController {
    private final RoutingService service;
    private final ResultStepBuilder builder = new ResultStepBuilder();
    private Stock stock = Stock.empty();

    public ResultStepsController(RoutingService service) {
        this.service = service;
    }

    public void setStock(Stock stock) {
        this.stock = stock != null ? stock : Stock.empty();
    }

    public void calculateRecipeTarget(ResultStepsState state, RecipeEntry<?> recipe, int times) {
        RoutingOutcome outcome = service.route(
            RoutingRequest.builder()
                .targetRecipe(recipe, times)
                .stock(stock)
                .budget(RoutingBudget.assuredDefault())
                .build()
        );
        state.setOutcome(outcome);
        state.setPlan(outcome.plan());
        if (outcome.plan() != null) {
            state.setTree(builder.fromPlan(outcome.plan(), service.graph()));
        }
    }
}
