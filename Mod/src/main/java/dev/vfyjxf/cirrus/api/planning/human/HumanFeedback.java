package dev.vfyjxf.cirrus.api.planning.human;

public sealed interface HumanFeedback
        permits AnswerPromptFeedback, ApplyRulesFeedback, OverrideStockFeedback,
                DeclareOwnedMachineFeedback, RejectStepFeedback, PinResourceRecipeFeedback {
}
