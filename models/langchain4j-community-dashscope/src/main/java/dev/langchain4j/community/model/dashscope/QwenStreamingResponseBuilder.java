package dev.langchain4j.community.model.dashscope;

import static dev.langchain4j.community.model.dashscope.QwenHelper.answerFrom;
import static dev.langchain4j.community.model.dashscope.QwenHelper.chatResponseFrom;
import static dev.langchain4j.community.model.dashscope.QwenHelper.hasAnswer;
import static dev.langchain4j.internal.Utils.getOrDefault;
import static dev.langchain4j.internal.Utils.isNotNullOrBlank;
import static dev.langchain4j.internal.Utils.isNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;

import com.alibaba.dashscope.aigc.generation.GenerationOutput;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.aigc.generation.GenerationUsage;
import com.alibaba.dashscope.aigc.generation.SearchInfo;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationOutput;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationUsage;
import com.alibaba.dashscope.common.DashScopeResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.MessageContentBase;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import com.alibaba.dashscope.tools.ToolCallBase;
import com.alibaba.dashscope.tools.ToolCallFunction;
import com.alibaba.dashscope.tools.codeinterpretertool.ToolCallCodeInterpreter;
import com.alibaba.dashscope.tools.search.ToolCallQuarkSearch;
import com.google.gson.JsonObject;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QwenStreamingResponseBuilder {
    private final String modelName;
    private final boolean incrementalOutput;
    private GenerationResult accumulatedGenerationResult;
    private MultiModalConversationResult accumulatedMultiModalConversationResult;

    public QwenStreamingResponseBuilder(String modelName, boolean incrementalOutput) {
        this.modelName = ensureNotBlank(modelName, "modelName");
        this.incrementalOutput = incrementalOutput;
    }

    public String append(GenerationResult partialResponse) {
        if (partialResponse == null) {
            return null;
        }

        String partialContent = null;
        if (hasAnswer(partialResponse)) {
            partialContent = answerFrom(partialResponse);
            if (!incrementalOutput && hasGenerationResult()) {
                String generatedContent = answerFrom(accumulatedGenerationResult);
                partialContent = partialContent.substring(generatedContent.length());
            }
        }

        accumulatedGenerationResult = hasGenerationResult()
                ? mergeResult(accumulatedGenerationResult, partialResponse)
                : mergeResult(newGenerationResult(), partialResponse);

        return partialContent;
    }

    public String append(MultiModalConversationResult partialResponse) {
        if (partialResponse == null) {
            return null;
        }

        String partialContent = null;
        if (hasAnswer(partialResponse)) {
            partialContent = answerFrom(partialResponse);
            if (!incrementalOutput && hasMultiModalConversationResult()) {
                String generatedContent = answerFrom(accumulatedMultiModalConversationResult);
                partialContent = partialContent.substring(generatedContent.length());
            }
        }

        accumulatedMultiModalConversationResult = hasMultiModalConversationResult()
                ? mergeResult(accumulatedMultiModalConversationResult, partialResponse)
                : mergeResult(newMultiModalConversationResult(), partialResponse);

        return partialContent;
    }

    public ChatResponse build() {
        if (hasGenerationResult()) {
            return chatResponseFrom(modelName, accumulatedGenerationResult);
        } else if (hasMultiModalConversationResult()) {
            return chatResponseFrom(modelName, accumulatedMultiModalConversationResult);
        } else {
            return null;
        }
    }

    private boolean hasGenerationResult() {
        return accumulatedGenerationResult != null;
    }

    private boolean hasMultiModalConversationResult() {
        return accumulatedMultiModalConversationResult != null;
    }

    private GenerationResult mergeResult(GenerationResult previous, GenerationResult current) {
        String requestId = getOrDefault(current.getRequestId(), previous.getRequestId());
        GenerationUsage usage = getOrDefault(current.getUsage(), previous.getUsage());
        GenerationOutput output = mergeOutput(previous.getOutput(), current.getOutput());

        GenerationResult result = newGenerationResult();
        result.setRequestId(requestId);
        result.setUsage(usage);
        result.setOutput(output);

        return result;
    }

    private GenerationOutput mergeOutput(GenerationOutput previous, GenerationOutput current) {
        GenerationOutput output = new GenerationOutput();

        String finishReason = getOrDefault(current.getFinishReason(), previous.getFinishReason());
        String text = merge(current.getText(), previous.getText());
        List<GenerationOutput.Choice> choices = mergeChoices(output, previous.getChoices(), current.getChoices());
        SearchInfo searchInfo = mergeSearchInfo(previous.getSearchInfo(), current.getSearchInfo());

        output.setFinishReason(finishReason);
        output.setText(text);
        output.setChoices(choices);
        output.setSearchInfo(searchInfo);

        return output;
    }

    private SearchInfo mergeSearchInfo(SearchInfo previous, SearchInfo current) {
        if (previous == null) {
            return current;
        }
        if (current == null) {
            return previous;
        }
        List<SearchInfo.SearchResult> searchResults = merge(previous.getSearchResults(), current.getSearchResults());
        return SearchInfo.builder().searchResults(searchResults).build();
    }

    private List<GenerationOutput.Choice> mergeChoices(
            GenerationOutput output, List<GenerationOutput.Choice> previous, List<GenerationOutput.Choice> current) {
        // in most cases, there is only one.
        List<GenerationOutput.Choice> choices = new ArrayList<>(1);
        GenerationOutput.Choice lastPreviousChoice = null;

        if (!isNullOrEmpty(previous)) {
            lastPreviousChoice = previous.get(previous.size() - 1);
            if (previous.size() > 1) {
                choices.addAll(previous.subList(0, previous.size() - 1));
            }
        }

        if (!isNullOrEmpty(current)) {
            var iterator = current.iterator();
            var firstChoice = iterator.next();
            // the first one should be merged with previous last one
            choices.add(mergeChoice(output, lastPreviousChoice, firstChoice));
            while (iterator.hasNext()) {
                choices.add(iterator.next());
            }
        } else {
            if (lastPreviousChoice != null) {
                choices.add(lastPreviousChoice);
            }
        }

        return choices;
    }

    private GenerationOutput.Choice mergeChoice(
            GenerationOutput output, GenerationOutput.Choice previous, GenerationOutput.Choice current) {
        if (previous == null) {
            return current;
        }
        if (current == null) {
            return previous;
        }

        Integer index = getOrDefault(current.getIndex(), previous.getIndex());
        String finishReason = getOrDefault(current.getFinishReason(), previous.getFinishReason());
        Message message = mergeMessage(previous.getMessage(), current.getMessage());

        GenerationOutput.Choice choice = output.new Choice();
        choice.setIndex(index);
        choice.setFinishReason(finishReason);
        choice.setMessage(message);

        return choice;
    }

    private Message mergeMessage(Message previous, Message current) {
        if (previous == null) {
            return current;
        }
        if (current == null) {
            return previous;
        }

        String content = mergeContent(previous.getContent(), current.getContent());
        String reasoningContent = merge(previous.getReasoningContent(), current.getReasoningContent());
        String role = getOrDefault(current.getRole(), previous.getRole());
        role = getOrDefault(role, Role.ASSISTANT.getValue());
        String name = getOrDefault(current.getName(), previous.getName());
        List<MessageContentBase> contents = merge(previous.getContents(), current.getContents());
        List<ToolCallBase> toolCalls = mergeToolCalls(previous.getToolCalls(), current.getToolCalls());
        String toolCallId = getOrDefault(current.getToolCallId(), previous.getToolCallId());

        return Message.builder()
                .content(content)
                .contents(contents)
                .toolCalls(toolCalls)
                .toolCallId(toolCallId)
                .name(name)
                .role(role)
                .reasoningContent(reasoningContent)
                .build();
    }

    private String mergeContent(String previous, String current) {
        return incrementalOutput ? merge(previous, current) : current;
    }

    private List<ToolCallBase> mergeToolCalls(List<ToolCallBase> previous, List<ToolCallBase> current) {
        // in most cases, there is only one
        List<ToolCallBase> toolCalls = new ArrayList<>(1);
        ToolCallBase lastPreviousTooCall = null;

        if (!isNullOrEmpty(previous)) {
            lastPreviousTooCall = previous.get(previous.size() - 1);
            if (previous.size() > 1) {
                toolCalls.addAll(previous.subList(0, previous.size() - 1));
            }
        }

        if (!isNullOrEmpty(current)) {
            var iterator = current.iterator();
            var firstToolCall = iterator.next();
            // the first one should be merged with previous last one
            if (isNotNullOrBlank(firstToolCall.getId())) {
                if (lastPreviousTooCall != null) {
                    toolCalls.add(lastPreviousTooCall);
                }
                toolCalls.add(firstToolCall);
            } else {
                toolCalls.add(mergeToolCall(lastPreviousTooCall, firstToolCall));
            }
            while (iterator.hasNext()) {
                toolCalls.add(iterator.next());
            }
        } else {
            if (lastPreviousTooCall != null) {
                toolCalls.add(lastPreviousTooCall);
            }
        }

        return toolCalls;
    }

    private ToolCallBase mergeToolCall(ToolCallBase previous, ToolCallBase current) {
        if (previous == null) {
            return current;
        }

        String id = (isNotNullOrBlank(current.getId()) ? current.getId() : previous.getId());
        String type = getOrDefault(current.getType(), previous.getType());

        if (previous instanceof ToolCallFunction previousToolCallFunction
                && current instanceof ToolCallFunction currentToolCallFunction) {
            ToolCallFunction newToolCall = new ToolCallFunction();
            ToolCallFunction.CallFunction callFunction = mergeToolCallFunction(
                    newToolCall, previousToolCallFunction.getFunction(), currentToolCallFunction.getFunction());
            newToolCall.setFunction(callFunction);
            newToolCall.setId(id);
            newToolCall.setType(type);
            return newToolCall;
        } else if (current instanceof ToolCallCodeInterpreter) {
            ToolCallCodeInterpreter newToolCall = new ToolCallCodeInterpreter();
            newToolCall.setId(id);
            newToolCall.setType(type);
            return newToolCall;
        } else if (previous instanceof ToolCallQuarkSearch previousQuarkToolCall
                && current instanceof ToolCallQuarkSearch currentQuarkToolCall) {
            Map<String, String> quarkSearch =
                    merge(previousQuarkToolCall.getQuarkSearch(), currentQuarkToolCall.getQuarkSearch());
            ToolCallQuarkSearch newToolCall = new ToolCallQuarkSearch();
            newToolCall.setId(id);
            newToolCall.setType(type);
            newToolCall.setQuarkSearch(quarkSearch);
            return newToolCall;
        } else {
            return current;
        }
    }

    private ToolCallFunction.CallFunction mergeToolCallFunction(
            ToolCallFunction toolCallFunction,
            ToolCallFunction.CallFunction previous,
            ToolCallFunction.CallFunction current) {
        if (previous == null) {
            return current;
        }

        String name = merge(previous.getName(), current.getName());
        String arguments = merge(previous.getArguments(), current.getArguments());
        String output = merge(previous.getOutput(), current.getOutput());

        ToolCallFunction.CallFunction callFunction = toolCallFunction.new CallFunction();
        callFunction.setName(name);
        callFunction.setArguments(arguments);
        callFunction.setOutput(output);
        return callFunction;
    }

    private MultiModalConversationResult mergeResult(
            MultiModalConversationResult previous, MultiModalConversationResult current) {
        String requestId = getOrDefault(current.getRequestId(), previous.getRequestId());
        MultiModalConversationUsage usage = getOrDefault(current.getUsage(), previous.getUsage());
        MultiModalConversationOutput output = mergeOutput(previous.getOutput(), current.getOutput());

        MultiModalConversationResult result = newMultiModalConversationResult();
        result.setRequestId(requestId);
        result.setUsage(usage);
        result.setOutput(output);

        return result;
    }

    private MultiModalConversationOutput mergeOutput(
            MultiModalConversationOutput previous, MultiModalConversationOutput current) {
        List<MultiModalConversationOutput.Choice> choices = mergeChoices(previous.getChoices(), current.getChoices());

        MultiModalConversationOutput output = new MultiModalConversationOutput();
        output.setChoices(choices);

        return output;
    }

    private List<MultiModalConversationOutput.Choice> mergeChoices(
            List<MultiModalConversationOutput.Choice> previous, List<MultiModalConversationOutput.Choice> current) {
        // in most cases, there is only one.
        List<MultiModalConversationOutput.Choice> choices = new ArrayList<>(1);
        MultiModalConversationOutput.Choice lastPreviousChoice = null;

        if (!isNullOrEmpty(previous)) {
            lastPreviousChoice = previous.get(previous.size() - 1);
            if (previous.size() > 1) {
                choices.addAll(previous.subList(0, previous.size() - 1));
            }
        }

        if (!isNullOrEmpty(current)) {
            var iterator = current.iterator();
            var firstChoice = iterator.next();
            // the first one should be merged with previous last one
            choices.add(mergeChoice(lastPreviousChoice, firstChoice));
            while (iterator.hasNext()) {
                choices.add(iterator.next());
            }
        } else {
            if (lastPreviousChoice != null) {
                choices.add(lastPreviousChoice);
            }
        }

        return choices;
    }

    private MultiModalConversationOutput.Choice mergeChoice(
            MultiModalConversationOutput.Choice previous, MultiModalConversationOutput.Choice current) {
        if (previous == null) {
            return current;
        }
        if (current == null) {
            return previous;
        }

        String finishReason = getOrDefault(current.getFinishReason(), previous.getFinishReason());
        MultiModalMessage message = mergeMessage(previous.getMessage(), current.getMessage());

        MultiModalConversationOutput.Choice choice = new MultiModalConversationOutput.Choice();
        choice.setFinishReason(finishReason);
        choice.setMessage(message);

        return choice;
    }

    private MultiModalMessage mergeMessage(MultiModalMessage previous, MultiModalMessage current) {
        if (previous == null) {
            return current;
        }
        if (current == null) {
            return previous;
        }

        List<Map<String, Object>> contents = mergeContents(previous.getContent(), current.getContent());
        String role = getOrDefault(current.getRole(), previous.getRole());
        role = getOrDefault(role, Role.ASSISTANT.getValue());

        return MultiModalMessage.builder().content(contents).role(role).build();
    }

    private List<Map<String, Object>> mergeContents(
            List<Map<String, Object>> previous, List<Map<String, Object>> current) {
        // in most cases, there is only one.
        List<Map<String, Object>> contents = new ArrayList<>(1);
        Map<String, Object> lastPreviousContent = null;

        if (!isNullOrEmpty(previous)) {
            lastPreviousContent = previous.get(previous.size() - 1);
            if (previous.size() > 1) {
                contents.addAll(previous.subList(0, previous.size() - 1));
            }
        }

        if (!isNullOrEmpty(current)) {
            var iterator = current.iterator();
            var firstContent = iterator.next();
            // the first one should be merged with previous last one
            contents.add(mergeContent(lastPreviousContent, firstContent));
            while (iterator.hasNext()) {
                contents.add(iterator.next());
            }
        } else {
            if (lastPreviousContent != null) {
                contents.add(lastPreviousContent);
            }
        }

        return contents;
    }

    private Map<String, Object> mergeContent(Map<String, Object> previous, Map<String, Object> current) {
        if (!incrementalOutput) {
            return merge(previous, current);
        }

        if (previous == null) {
            return current;
        }
        if (current == null) {
            return previous;
        }
        Map<String, Object> merged = new HashMap<>(previous);
        for (var key : current.keySet()) {
            if (previous.get(key) instanceof String previousValue && current.get(key) instanceof String currentValue) {
                merged.put(key, merge(previousValue, currentValue));
            } else {
                merged.put(key, current.get(key));
            }
        }
        return merged;
    }

    private static GenerationResult newGenerationResult() {
        DashScopeResult emptyResult = new DashScopeResult();
        emptyResult.setOutput(new JsonObject());
        return GenerationResult.fromDashScopeResult(emptyResult);
    }

    private static MultiModalConversationResult newMultiModalConversationResult() {
        DashScopeResult emptyResult = new DashScopeResult();
        emptyResult.setOutput(new JsonObject());
        return MultiModalConversationResult.fromDashScopeResult(emptyResult);
    }

    private static <K, V> Map<K, V> merge(Map<K, V> previous, Map<K, V> current) {
        if (previous == null) {
            return current;
        }
        if (current == null) {
            return previous;
        }
        Map<K, V> merged = new HashMap<>(previous);
        merged.putAll(current);
        return merged;
    }

    private static <T> List<T> merge(List<T> previous, List<T> current) {
        if (previous == null) {
            return current;
        }
        if (current == null) {
            return previous;
        }
        List<T> merged = new ArrayList<>(previous.size() + current.size());
        merged.addAll(previous);
        merged.addAll(current);
        return merged;
    }

    private static String merge(String previous, String current) {
        if (previous == null) {
            return current;
        }
        if (current == null) {
            return previous;
        }
        return previous + current;
    }
}
