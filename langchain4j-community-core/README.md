# LangChain4j Community Core

## Prompt repetition

This module includes optional prompt repetition components.

Prompt repetition rewrites:

```text
Q -> Q\nQ
```

Use it only where it helps your workload.

### Components

- `PromptRepeatingInputGuardrail` for non-RAG user input.
- `RepeatingQueryTransformer` for RAG retrieval queries.
- `PromptRepetitionPolicy` shared by both.

### Modes

- `NEVER`: disable.
- `ALWAYS`: repeat when eligible.
- `AUTO`: conservative mode:
  - skip already repeated input
  - skip very long input (`maxChars`)
  - skip reasoning-intent prompts (`reasoningKeywords`)

### Non-RAG usage

```java
PromptRepetitionPolicy policy = PromptRepetitionPolicy.builder()
        .mode(PromptRepetitionMode.AUTO)
        .maxChars(8_000)
        .build();

Assistant assistant = AiServices.builder(Assistant.class)
        .chatModel(chatModel)
        .inputGuardrails(new PromptRepeatingInputGuardrail(policy))
        .build();
```

### RAG usage

Repeat the retrieval query only. Do not repeat the full augmented prompt.

```java
PromptRepetitionPolicy policy = PromptRepetitionPolicy.builder()
        .mode(PromptRepetitionMode.AUTO)
        .maxChars(8_000)
        .build();

RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
        .queryTransformer(new RepeatingQueryTransformer(policy))
        .build();
```

### Safety behavior

- Input guardrail rewrites only single-text user messages.
- Input guardrail skips by default when RAG augmentation is detected.
- Idempotence is built in:
  - first pass: `Q -> Q\nQ`
  - repeated pass: `SKIPPED_ALREADY_REPEATED`

### Rollout checklist

1. Start with `AUTO`.
2. Keep `maxChars` conservative.
3. Tune `reasoningKeywords` for your workload.
4. Track apply/skip reasons during A/B evaluation.
