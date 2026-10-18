package dev.langchain4j.community.mcp.server;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an MCP resource, exposed through {@code resources/list} and
 * {@code resources/read}.
 *
 * <p>The annotated method must be public, take no parameters, and return the resource content as a
 * {@link String}. Each URI must be unique within a server.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @Resource(
 *         uri = "config://app/settings",
 *         name = "Application Settings",
 *         description = "Current application configuration",
 *         mimeType = "application/json")
 * public String getAppSettings() {
 *     return objectMapper.writeValueAsString(settingsMap);
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Resource {

    /** URI that uniquely identifies the resource, e.g. {@code "config://app/settings"}. */
    String uri();

    /** Human readable name shown to clients. */
    String name();

    /** Optional description of what the resource contains. */
    String description() default "";

    /** MIME type of the content returned by the method. */
    String mimeType() default "text/plain";
}
