package dev.langchain4j.community.model.xinference;

import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import java.util.Objects;

public class XinferenceChatResponseMetadata extends ChatResponseMetadata {

    private final String reasoningContent;

    private XinferenceChatResponseMetadata(Builder builder) {
        super(builder);
        this.reasoningContent = builder.reasoningContent;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        XinferenceChatResponseMetadata that = (XinferenceChatResponseMetadata) o;
        return Objects.equals(reasoningContent, that.reasoningContent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), reasoningContent);
    }

    @Override
    public String toString() {
        return "XinferenceChatResponseMetadata{" + "reasoningContent='" + reasoningContent + '\'' + '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends ChatResponseMetadata.Builder<Builder> {

        private String reasoningContent;

        public Builder reasoningContent(String reasoningContent) {
            this.reasoningContent = reasoningContent;
            return this;
        }

        @Override
        public XinferenceChatResponseMetadata build() {
            return new XinferenceChatResponseMetadata(this);
        }
    }
}
