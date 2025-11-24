package dev.langchain4j.community.store.embedding.redis.vectorsets;

import dev.langchain4j.data.document.Metadata;

public record Sentence(String name, String text, int age){
    public Metadata toMetadata() {
        return Metadata
                .from("name", name)
                .put("age", age);
    }
}
