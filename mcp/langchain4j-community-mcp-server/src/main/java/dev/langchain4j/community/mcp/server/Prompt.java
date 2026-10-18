package dev.langchain4j.community.mcp.server;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an MCP prompt template, exposed through {@code prompts/list} and
 * {@code prompts/get}.
 *
 * <p>The annotated method must be public, return {@code List<PromptMessage>}, and may declare
 * parameters annotated with {@link PromptArg}. Clients fill those arguments through
 * {@code prompts/get}.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @Prompt(name = "code-review", description = "Review code for bugs, style issues, and improvements")
 * public List<PromptMessage> codeReview(
 *         @PromptArg(name = "code", description = "The code to review", required = true) String code,
 *         @PromptArg(name = "language", description = "Programming language") String language) {
 *     return List.of(PromptMessage.user("Please review this code:\n\n```\n%s\n```".formatted(code)));
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Prompt {

    /** Name that clients use in {@code prompts/get}. Must be unique within a server. */
    String name();

    /** Optional description of what the prompt does. */
    String description() default "";
}
