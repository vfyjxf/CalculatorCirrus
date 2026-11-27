package dev.vfyjxf.cirrus.api.planning.human;

import java.util.Objects;

public record AnswerPromptFeedback(String promptId, String chosenOptionId, boolean persistToWorld)
        implements
            HumanFeedback {

    public AnswerPromptFeedback {
        Objects.requireNonNull(promptId, "promptId");
        Objects.requireNonNull(chosenOptionId, "chosenOptionId");
    }
}
