package dev.langchain4j.community.mcp.server;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares one argument of a {@link Prompt} method. Arguments are advertised in
 * {@code prompts/list} and filled from the client's {@code prompts/get} request.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface PromptArg {

    /** Argument name the client sends in the {@code arguments} map. */
    String name();

    /** Optional description shown to clients. */
    String description() default "";

    /** Whether the client must provide this argument. */
    boolean required() default false;
}
