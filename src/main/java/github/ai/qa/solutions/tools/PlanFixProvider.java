package github.ai.qa.solutions.tools;

/**
 * Minimal contract for producing a fix plan based on validation errors and user context.
 */
public interface PlanFixProvider {
    /**
     * Returns a minimal-change plan to fix JSON according to schema errors and user prompt.
     *
     * @param errors    raw validation errors
     * @param userPromt user prompt or scenario constraints
     * @return plan text (may be empty but not null)
     */
    String thinkHowToFixJson(String errors, String userPromt);
}
