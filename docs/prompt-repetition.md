# Prompt Repetition in LangChain4j Community

This guide shows how to use prompt repetition safely in both non-RAG and RAG pipelines.

Reference paper:
- [Prompt Repetition: Foundation Models Know More When You Say It Again](https://arxiv.org/html/2512.14982v1)

## What prompt repetition does

Prompt repetition rewrites one text prompt into a duplicated form:

```text
Q -> Q\nQ
```

This is a pure input transformation:
- output schema stays unchanged
- no parser changes are required
- it can be added or removed as a drop-in component

## Components

Community core provides two integration points:

- `PromptRepeatingInputGuardrail`
  Use for non-RAG user input in AI Services.

- `RepeatingQueryTransformer`
  Use for RAG retrieval queries only (query rewriting before retrieval).

Both use the same `PromptRepetitionPolicy`.

## Policy modes

- `NEVER`
  Disable repetition.

- `ALWAYS`
  Always repeat when input is eligible (except idempotence protection).

- `AUTO`
  Conservative mode designed for production:
  - skips already repeated input
  - skips very long input (`maxChars`)
  - skips prompts that look like explicit reasoning requests (`reasoningKeywords`)

## Non-RAG usage (AI Services InputGuardrail)

```java
PromptRepetitionPolicy policy = PromptRepetitionPolicy.builder()
        .mode(PromptRepetitionMode.AUTO)
        .maxChars(8_000)
        .reasoningKeywords(Set.of(
                "step by step",
                "chain of thought",
                "show your reasoning"))
        .build();

InputGuardrail guardrail = new PromptRepeatingInputGuardrail(policy);
```

With AI Services annotations:

```java
interface Assistant {

    @InputGuardrails(PromptRepeatingInputGuardrail.class)
    String chat(String userMessage);
}
```

Important behavior:
- non-text/multimodal user messages are skipped
- if RAG augmentation is already present, guardrail skips by default

## RAG usage (QueryTransformer)

Use query-side repetition only. Do not repeat the full augmented prompt with retrieved context.

```java
PromptRepetitionPolicy policy = PromptRepetitionPolicy.builder()
        .mode(PromptRepetitionMode.AUTO)
        .maxChars(8_000)
        .build();

RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
        .queryTransformer(new RepeatingQueryTransformer(policy))
        .build();
```

This keeps retrieval context cost stable while still allowing query rewriting gains.

## Idempotence and safety

Idempotence protection is built in:
- first pass: `Q -> Q\nQ`
- repeated pass: skipped as `SKIPPED_ALREADY_REPEATED`

This prevents token-cost blowups in retries, tool loops, and chained pipelines.

## Recommended rollout

1. Start with `AUTO`.
2. Keep `maxChars` conservative.
3. Expand or reduce `reasoningKeywords` based on your workload.
4. Use `PromptRepetitionDecision.reason()` during evaluation to understand skip/apply distribution.

## A/B evaluation checklist

Use the same prompts, model config, and retrieval setup across both variants:

- Variant A: baseline (no repetition)
- Variant B: repetition enabled

Track at least:
- task success or quality metric
- input token delta
- latency distribution
- apply/skip reasons (`APPLIED`, `SKIPPED_TOO_LONG`, `SKIPPED_REASONING_INTENT`, etc.)

Practical recommendation:
- evaluate non-RAG and RAG as separate buckets
- in RAG, apply repetition at query-transform stage, not on augmented context

## Validation snapshot (PR evidence)

The following results were produced on February 16, 2026 using the implementation in this PR:

### 1) Functional correctness

- `langchain4j-community-core` module tests: `108/108` passed.
- Additional end-to-end harness checks using real `AiServices` wiring:
  - non-RAG input rewrite works in `ALWAYS`.
  - `AUTO` skips explicit reasoning prompts.
  - idempotence prevents repeated growth (`Q -> Q\nQ`, not `Q\nQ\nQ\nQ`).
  - `PromptRepeatingInputGuardrail` skips when RAG augmentation is present.
  - `RepeatingQueryTransformer` preserves `Query.metadata`.
  - `RepeatingQueryTransformer` executes inside `DefaultRetrievalAugmentor` flow.

These checks confirm the feature is functional and implements the intended behavior.

### 2) Quantified value (A/B)

Setup:
- Model: `gpt-4.1-mini`
- Variant A: baseline (no repetition)
- Variant B: repetition enabled through PR components
- Metric: exact-match accuracy, plus token/latency deltas

Non-RAG paper-aligned buckets (`mode=ALWAYS`):

| Bucket | Cases | Exact A | Exact B | Delta | Avg Tokens A | Avg Tokens B | Token Delta |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| NameIndex | 120 | 61.67% | 79.17% | +17.50pp | 136.45 | 262.98 | +126.53 |
| OptionsFirst | 120 | 80.00% | 100.00% | +20.00pp | 288.18 | 564.35 | +276.18 |
| MiddleMatch | 120 | 97.50% | 100.00% | +2.50pp | 159.87 | 309.82 | +149.95 |
| **Aggregate** | **360** | **79.72%** | **93.06%** | **+13.33pp** | **194.83** | **379.05** | **+184.22** |

Reasoning-control bucket (`mode=AUTO`):
- Cases: `80`
- Applied rate: `0%`
- Reason distribution: `SKIPPED_REASONING_INTENT=80`

This confirms `AUTO` behaves conservatively in reasoning-like prompts.

RAG bucket (`mode=AUTO`, query-side repetition via `RepeatingQueryTransformer`):
- Cases: `30`
- Exact match: `1.00 -> 1.00` (no quality change in this bucket)

### Interpretation

- The PR code is functional and enforces the expected safety gates.
- Prompt repetition shows measurable gains in non-RAG objective tasks.
- Gains are task-dependent: high-baseline tasks show smaller improvements.
- `AUTO` mode remains conservative for reasoning prompts and should be the default rollout mode.
