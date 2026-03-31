package dev.vfyjxf.cirrus.api.recipe.execution;

/**
 * Declares which runtime side can execute a translated interaction step.
 */
public sealed interface ExecutionSideRequirement permits
    ExecutionSideRequirement.EnvironmentAgnostic,
    ExecutionSideRequirement.Client,
    ExecutionSideRequirement.Server,
    ExecutionSideRequirement.Player {

    //region shared instances

    ExecutionSideRequirement environmentAgnostic = new EnvironmentAgnostic();
    ExecutionSideRequirement client = new Client();
    ExecutionSideRequirement server = new Server();
    ExecutionSideRequirement player = new Player();
    ExecutionSideRequirement manual = player;
    ExecutionSideRequirement clientSide = client;
    ExecutionSideRequirement serverSide = server;
    ExecutionSideRequirement manualPlayerAction = player;

    //endregion

    //region contract

    boolean allowsClientExecution();

    boolean allowsServerExecution();

    boolean onlyRunsAutomatically();

    //endregion

    //region variants

    record EnvironmentAgnostic() implements ExecutionSideRequirement {
        @Override
        public boolean allowsClientExecution() {
            return true;
        }

        @Override
        public boolean allowsServerExecution() {
            return true;
        }

        @Override
        public boolean onlyRunsAutomatically() {
            return true;
        }
    }

    record Client() implements ExecutionSideRequirement {
        @Override
        public boolean allowsClientExecution() {
            return true;
        }

        @Override
        public boolean allowsServerExecution() {
            return false;
        }

        @Override
        public boolean onlyRunsAutomatically() {
            return true;
        }
    }

    record Server() implements ExecutionSideRequirement {
        @Override
        public boolean allowsClientExecution() {
            return false;
        }

        @Override
        public boolean allowsServerExecution() {
            return true;
        }

        @Override
        public boolean onlyRunsAutomatically() {
            return true;
        }
    }

    record Player() implements ExecutionSideRequirement {
        @Override
        public boolean allowsClientExecution() {
            return true;
        }

        @Override
        public boolean allowsServerExecution() {
            return false;
        }

        @Override
        public boolean onlyRunsAutomatically() {
            return false;
        }
    }

    //endregion
}
