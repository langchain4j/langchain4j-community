# LangChain4j Community Outputgate

An output guardrail that checks structured (JSON) model output against an
[outputgate](https://github.com/ravismash/outputgate) contract, and reprompts the model with
the exact violations when the output fails.

JSON mode and structured outputs make the model return JSON of the right shape. They do not
check what is in it: whether line values add up to the total, whether an ID matches its format,
whether a quoted figure is really in the source text. A contract checks those things with
deterministic gates, so the same output always gets the same verdict and no second model call is
needed to judge it.

## Gates

| Gate | Checks |
|---|---|
| `SchemaGate` | JSON Schema (types, required fields, enums, ranges) |
| `MathGate` | arithmetic between fields: sums, products, ranges, custom rules |
| `RegexGate` | field formats |
| `ProvenanceGate` | each value carries a quote, and the quote occurs in the source document |
| `BudgetGate` | output tokens (taken from the response's `TokenUsage`) |

## Usage

```java
Contract contract = Contract.builder()
        .gate(SchemaGate.fromClasspath("survey.schema.json"))
        .gate(MathGate.builder()
                .sumEquals("options[*].count", "total_responses")
                .build())
        .build();

SurveyExtractor extractor = AiServices.builder(SurveyExtractor.class)
        .chatModel(chatModel)
        .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
        .outputGuardrails(new OutputgateOutputGuardrail(contract))
        .build();
```

When the output fails, the model gets a reprompt like this, and the AI service calls it again up
to `OutputGuardrailsConfig.maxRetries()` times:

```text
Your previous response did not pass these checks:
- total_responses: sum of options[*].count is 120 but total_responses is 130 (tolerance 0) (math)
Fix only what these checks report and return the whole corrected JSON, with no other text.
```

### Checking quotes against the source

For a contract with a grounded `ProvenanceGate`, tell the guardrail where the source document is:

```java
OutputgateOutputGuardrail guardrail = OutputgateOutputGuardrail.builder()
        .contract(contract)
        .source(OutputgateOutputGuardrail.retrievedContentAsSource()) // RAG contents, one page each
        // or .source(OutputgateOutputGuardrail.userMessageAsSource())
        .build();
```

### Options

- `repromptOnViolation(false)`: fail fatally instead of reprompting.
- `repromptMessage(verdict -> ...)`: write your own reprompt text.
- `stripCodeFence(false)`: check the text exactly as returned. By default a Markdown code fence
  around the JSON is removed, and the unwrapped JSON is passed on as the response.
