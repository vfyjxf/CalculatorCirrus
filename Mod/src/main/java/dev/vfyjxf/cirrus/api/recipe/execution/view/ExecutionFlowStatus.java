package dev.vfyjxf.cirrus.api.recipe.execution.view;

/**
 * Visual status projected onto execution flow nodes for UI consumption.
 */
public sealed interface ExecutionFlowStatus permits
    ExecutionFlowStatus.Pending,
    ExecutionFlowStatus.Running,
    ExecutionFlowStatus.Paused,
    ExecutionFlowStatus.Completed,
    ExecutionFlowStatus.Failed {

    //region shared instances

    ExecutionFlowStatus pending = new Pending();
    ExecutionFlowStatus running = new Running();
    ExecutionFlowStatus paused = new Paused();
    ExecutionFlowStatus completed = new Completed();
    ExecutionFlowStatus failed = new Failed();

    //endregion

    boolean isTerminal();

    //region variants

    record Pending() implements ExecutionFlowStatus {
        @Override
        public boolean isTerminal() {
            return false;
        }
    }

    record Running() implements ExecutionFlowStatus {
        @Override
        public boolean isTerminal() {
            return false;
        }
    }

    record Paused() implements ExecutionFlowStatus {
        @Override
        public boolean isTerminal() {
            return false;
        }
    }

    record Completed() implements ExecutionFlowStatus {
        @Override
        public boolean isTerminal() {
            return true;
        }
    }

    record Failed() implements ExecutionFlowStatus {
        @Override
        public boolean isTerminal() {
            return true;
        }
    }

    //endregion
}
