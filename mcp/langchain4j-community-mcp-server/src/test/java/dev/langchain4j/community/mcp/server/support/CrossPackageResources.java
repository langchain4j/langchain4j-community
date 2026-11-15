package dev.langchain4j.community.mcp.server.support;

import dev.langchain4j.community.mcp.server.Prompt;
import dev.langchain4j.community.mcp.server.PromptMessage;
import dev.langchain4j.community.mcp.server.Resource;
import java.util.List;

/**
 * Fixtures declared in a different package than {@code McpServer}, so that reflective access is
 * subject to real cross-package rules. Both holder classes are package-private on purpose.
 */
public final class CrossPackageResources {

    private CrossPackageResources() {}

    /** Public methods on a package-private class: not accessible from another package as-is. */
    public static Object packagePrivateHolder() {
        return new PackagePrivateHolder();
    }

    static class PackagePrivateHolder {

        @Resource(uri = "pkg://resource", name = "Package Private")
        public String read() {
            return "readable";
        }

        @Prompt(name = "pkg-prompt")
        public List<PromptMessage> prompt() {
            return List.of(PromptMessage.user("readable"));
        }
    }
}
