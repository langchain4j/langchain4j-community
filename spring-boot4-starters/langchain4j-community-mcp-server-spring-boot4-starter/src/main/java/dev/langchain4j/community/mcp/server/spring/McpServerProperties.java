package dev.langchain4j.community.mcp.server.spring;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(McpServerProperties.CONFIG_PREFIX)
public class McpServerProperties {

    public static final String CONFIG_PREFIX = "langchain4j.community.mcp.server";

    /** Whether to start the STDIO server and close the application when its input reaches EOF. */
    private boolean enabled;

    /** Names of Spring singleton beans whose declared @Tool methods should be exposed. */
    private List<String> toolBeanNames = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getToolBeanNames() {
        return toolBeanNames;
    }

    public void setToolBeanNames(List<String> toolBeanNames) {
        this.toolBeanNames = toolBeanNames;
    }
}
