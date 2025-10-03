package dev.langchain4j.community.model.oracle.oci.genai;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.CompleteToolCall;
import dev.langchain4j.model.chat.response.PartialToolCall;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.FinishReason;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class GenericStreamingTest {

    private TestStreamingChatResponseHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TestStreamingChatResponseHandler();
    }

    static void assertToolExecutionRequest(
            ToolExecutionRequest toolExecutionRequest, String id, String name, String arguments) {
        assertThat(toolExecutionRequest.id(), is(id));
        assertThat(toolExecutionRequest.name(), is(name));
        assertThat(toolExecutionRequest.arguments(), is(arguments));
    }

    @Test
    void streamedText() {
        String data =
                """
                data: {"index":0,"message":{"role":"ASSISTANT","content":[{"type":"TEXT","text":"THE"}]},"pad":"aaaaaaaa"}
                data: {"index":0,"message":{"role":"ASSISTANT","content":[{"type":"TEXT","text":" SUM"}]},"pad":"aa"}
                data: {"index":0,"message":{"role":"ASSISTANT","content":[{"type":"TEXT","text":" OF"}]},"pad":"aaaaa"}
                data: {"index":0,"message":{"role":"ASSISTANT","content":[{"type":"TEXT","text":" THE"}]},"pad":"aaa"}
                data: {"index":0,"message":{"role":"ASSISTANT","content":[{"type":"TEXT","text":" RESULTS"}]},"pad":"aaaa"}
                data: {"index":0,"message":{"role":"ASSISTANT","content":[{"type":"TEXT","text":" IS"}]},"pad":"aaaaaaaaaa"}
                data: {"index":0,"message":{"role":"ASSISTANT","content":[{"type":"TEXT","text":" "}]},"pad":"aaaaaaaaaa"}
                data: {"index":0,"message":{"role":"ASSISTANT","content":[{"type":"TEXT","text":"62"}]},"pad":"a"}
                data: {"index":0,"message":{"role":"ASSISTANT","content":[{"type":"TEXT","text":"."}]},"pad":"aaaaaaaaaaa"}
                data: {"index":0,"message":{"role":"ASSISTANT","content":[{"type":"TEXT","text":"0"}]},"pad":"aaaaa"}
                data: {"finishReason":"stop","pad":"aaaaaa"}
                """;
        var b = new GenericStreamingResponseBuilder("test", handler);
        Arrays.stream(data.split("\n")).forEach(b::parseAndAdd);
        var chatResponse = b.build();
        assertThat(chatResponse.aiMessage().hasToolExecutionRequests(), is(false));
        var toolExecutionRequests = chatResponse.aiMessage().toolExecutionRequests();
        assertThat(toolExecutionRequests.size(), is(0));
        assertThat(chatResponse.aiMessage().text(), is("THE SUM OF THE RESULTS IS 62.0"));
        assertThat(
                handler.partialResponses,
                contains("THE", " SUM", " OF", " THE", " RESULTS", " IS", " ", "62", ".", "0"));
        assertThat(handler.completeResponses, contains("THE SUM OF THE RESULTS IS 62.0"));
    }

    @Test
    void streamedText1() {
        String data =
                """
                data: {"index":0,"message":{"role":"ASSISTANT","content":[{"type":"TEXT","text":""}]},"pad":"aaaa"}
                data: {"index":0,"message":{"role":"ASSISTANT","content":[{"type":"TEXT","text":"Hello"}]},"pad":"aa"}
                data: {"index":0,"message":{"role":"ASSISTANT","content":[{"type":"TEXT","text":" "}]},"pad":"aaaaaa"}
                data: {"message":{"role":"ASSISTANT"},"finishReason":"stop","pad":"aaa"}
                """;
        var b = new GenericStreamingResponseBuilder("test", handler);
        Arrays.stream(data.split("\n")).forEach(b::parseAndAdd);
        var chatResponse = b.build();
        assertThat(chatResponse.aiMessage().hasToolExecutionRequests(), is(false));
        var toolExecutionRequests = chatResponse.aiMessage().toolExecutionRequests();
        assertThat(toolExecutionRequests.size(), is(0));
        assertThat(chatResponse.aiMessage().text(), is("Hello "));
        assertThat(handler.partialResponses, contains("Hello", " "));
        assertThat(handler.completeResponses, contains("Hello "));
    }

    @Test
    void streamedText2() {
        String data =
                """
                data: {"index":0,"message":{"role":"ASSISTANT","content":[{"type":"TEXT","text":""}]},"pad":"aaaaaaaa"}
                data: {"index":0,"message":{"role":"ASSISTANT","content":[{"type":"TEXT","text":""}]},"pad":"aaaaaaa"}
                data: {"message":{"role":"ASSISTANT","content":[{"type":"TEXT","text":"Hello "}]},"finishReason":"stop","pad":"a"}
                """;
        var b = new GenericStreamingResponseBuilder("test", handler);
        Arrays.stream(data.split("\n")).forEach(b::parseAndAdd);
        var chatResponse = b.build();
        assertThat(chatResponse.aiMessage().hasToolExecutionRequests(), is(false));
        var toolExecutionRequests = chatResponse.aiMessage().toolExecutionRequests();
        assertThat(toolExecutionRequests.size(), is(0));
        assertThat(chatResponse.aiMessage().text(), is("Hello "));
        System.out.println(handler.partialResponses);
        assertThat(handler.partialResponses, contains("Hello "));
        assertThat(handler.completeResponses, contains("Hello "));
    }

    @Test
    void multiToolSingleArg() {
        String data =
                """
                data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","id":"call_69943055","name":"LIST_PACKAGE_NAMES","arguments":"{\\"arg0\\":\\"HR\\"}"}]},"pad":"aa"}
                data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","id":"call_53265426","name":"LIST_PROCEDURE_NAMES","arguments":"{\\"arg0\\":\\"HR\\"}"}]},"pad":"a"}
                data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","id":"call_53405288","name":"LIST_FUNCTION_NAMES","arguments":"{\\"arg0\\":\\"HR\\"}"}]},"pad":"aaaaaaaaaa"}
                data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","id":"call_19110181","name":"LIST_TYPE_NAMES","arguments":"{\\"arg0\\":\\"HR\\"}"}]},"pad":"a"}
                data: {"finishReason":"tool_calls","pad":"aaaaaaaaa"}
                """;
        var b = new GenericStreamingResponseBuilder("test", handler);
        Arrays.stream(data.split("\n")).forEach(b::parseAndAdd);
        var chatResponse = b.build();
        assertThat(chatResponse.aiMessage().hasToolExecutionRequests(), is(true));
        var toolExecutionRequests = chatResponse.aiMessage().toolExecutionRequests();
        assertThat(toolExecutionRequests.size(), is(4));
        assertToolExecutionRequest(
                toolExecutionRequests.get(0), "call_69943055", "LIST_PACKAGE_NAMES", "{\"arg0\":\"HR\"}");
        assertToolExecutionRequest(
                toolExecutionRequests.get(1), "call_53265426", "LIST_PROCEDURE_NAMES", "{\"arg0\":\"HR\"}");
        assertToolExecutionRequest(
                toolExecutionRequests.get(2), "call_53405288", "LIST_FUNCTION_NAMES", "{\"arg0\":\"HR\"}");
        assertToolExecutionRequest(
                toolExecutionRequests.get(3), "call_19110181", "LIST_TYPE_NAMES", "{\"arg0\":\"HR\"}");
        assertThat(handler.completeResponses, contains((String) null));
    }

    @Test
    void multiToolMultiArgs() {
        String data =
                """
                data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","id":"call_83516431","name":"sqrt","arguments":"{\\"arg0\\":16}"}]},"pad":"aaaaaa"}
                data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","id":"call_26154935","name":"extractMagicalNumber","arguments":"{\\"arg1\\":778,\\"arg0\\":556}"}]},"pad":"a"}
                data: {"finishReason":"tool_calls","pad":"aa"}
                """;
        var b = new GenericStreamingResponseBuilder("test", handler);
        Arrays.stream(data.split("\n")).forEach(b::parseAndAdd);
        var chatResponse = b.build();
        assertThat(chatResponse.aiMessage().hasToolExecutionRequests(), is(true));
        var toolExecutionRequests = chatResponse.aiMessage().toolExecutionRequests();
        assertThat(toolExecutionRequests.size(), is(2));
        assertToolExecutionRequest(toolExecutionRequests.get(0), "call_83516431", "sqrt", "{\"arg0\":16}");
        assertToolExecutionRequest(
                toolExecutionRequests.get(1), "call_26154935", "extractMagicalNumber", "{\"arg1\":778,\"arg0\":556}");
        assertThat(handler.completeResponses, contains((String) null));
    }

    @Test
    void singleChunkedTool() {
        String data =
                """
                data: {"index":0,"message":{"role":"ASSISTANT",  "content":[{"type":"TEXT",    "text":""                                                          }]},"pad":"aa"}
                data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","id":"chatcmpl-tool-e78c012be89a4742a6ba7e4b0a05b0f2","name":"sqrt"}]},"pad":"aaaaaa"}
                data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":"{\\"arg0\\": \\""                                     }]},"pad":"aaaaaa"}
                data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":"16\\"}"                                               }]},"pad":"aaaaaaaaa"}
                data: {          "message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":""                                                     }]},"finishReason":"tool_calls","pad":"aaa"}
                """;
        var b = new GenericStreamingResponseBuilder("test", handler);
        Arrays.stream(data.split("\n")).forEach(b::parseAndAdd);
        var chatResponse = b.build();
        assertThat(chatResponse.finishReason(), is(FinishReason.TOOL_EXECUTION));
        assertThat(chatResponse.aiMessage().hasToolExecutionRequests(), is(true));
        var toolExecutionRequests = chatResponse.aiMessage().toolExecutionRequests();
        assertThat(toolExecutionRequests.size(), is(1));
        var firstToolExecReq = toolExecutionRequests.get(0);
        assertThat(firstToolExecReq.arguments(), is("{\"arg0\": \"16\"}"));
        assertThat(firstToolExecReq.id(), is("chatcmpl-tool-e78c012be89a4742a6ba7e4b0a05b0f2"));
        assertThat(handler.completeResponses, contains((String) null));
    }

    @Test
    void multiChunkedTool() {
        String data =
                """
                data: {"index":0,"message":{"role":"ASSISTANT","content":[{"type":"TEXT","text":""}]},"pad":"aaaaa"}
                data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","id":"chatcmpl-tool-e5f86a029","name":"sqrt"}]},"pad":"aaaaaa"}
                data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":"{\\"arg0\\": \\""}]},"pad":"aaa"}
                data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":"16\\"}"}]},"pad":"aaa"}
                data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":""}]},"pad":"aaaaaaa"}
                data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":""}]},"pad":"aa"}
                data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","id":"chatcmpl-tool-13ee961b3","name":"extractMagicalNumber"}]},"pad":"aaaaaa"}
                data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":"{\\"arg1\\": \\""}]},"pad":"aaa"}
                data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":"778\\""}]},"pad":"aaaaaaaaa"}
                data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":", \\"arg0\\": \\""}]},"pad":"aaaa"}
                data: {"index":0,"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":"556\\"}"}]},"pad":"a"}
                data: {"message":{"role":"ASSISTANT","toolCalls":[{"type":"FUNCTION","arguments":""}]},"finishReason":"tool_calls","pad":"aaaaaaaaa"}
                """;
        var b = new GenericStreamingResponseBuilder("test", handler);
        Arrays.stream(data.split("\n")).forEach(b::parseAndAdd);
        var chatResponse = b.build();
        assertThat(chatResponse.finishReason(), is(FinishReason.TOOL_EXECUTION));
        assertThat(chatResponse.aiMessage().hasToolExecutionRequests(), is(true));
        var toolExecutionRequests = chatResponse.aiMessage().toolExecutionRequests();
        assertThat(toolExecutionRequests.size(), is(2));
        assertToolExecutionRequest(
                toolExecutionRequests.get(0), "chatcmpl-tool-e5f86a029", "sqrt", "{\"arg0\": \"16\"}");
        assertToolExecutionRequest(
                toolExecutionRequests.get(1),
                "chatcmpl-tool-13ee961b3",
                "extractMagicalNumber",
                "{\"arg1\": \"778\", \"arg0\": \"556\"}");

        assertThat(
                handler.partialToolCalls.stream()
                        .map(PartialToolCall::partialArguments)
                        .toList(),
                contains("{\"arg0\": \"", "16\"}", "{\"arg1\": \"", "778\"", ", \"arg0\": \"", "556\"}"));

        assertThat(
                handler.partialToolCalls.stream().map(PartialToolCall::name).toList(),
                contains(
                        "sqrt",
                        "sqrt",
                        "extractMagicalNumber",
                        "extractMagicalNumber",
                        "extractMagicalNumber",
                        "extractMagicalNumber"));

        assertThat(
                handler.completeToolCalls.stream()
                        .map(CompleteToolCall::toolExecutionRequest)
                        .map(ToolExecutionRequest::arguments)
                        .toList(),
                contains("{\"arg0\": \"16\"}", "{\"arg1\": \"778\", \"arg0\": \"556\"}"));

        assertThat(handler.completeResponses, contains((String) null));
    }

    private static class TestStreamingChatResponseHandler implements StreamingChatResponseHandler {

        List<String> partialResponses = new ArrayList<>();
        List<String> completeResponses = new ArrayList<>();
        List<Throwable> completeErrors = new ArrayList<>();
        List<PartialToolCall> partialToolCalls = new ArrayList<>();
        private List<CompleteToolCall> completeToolCalls = new ArrayList<>();

        @Override
        public void onPartialResponse(final String s) {
            partialResponses.add(s);
        }

        @Override
        public void onCompleteResponse(final ChatResponse response) {
            completeResponses.add(response.aiMessage().text());
        }

        @Override
        public void onError(final Throwable throwable) {
            completeErrors.add(throwable);
        }

        @Override
        public void onPartialToolCall(final PartialToolCall partialToolCall) {
            partialToolCalls.add(partialToolCall);
        }

        @Override
        public void onCompleteToolCall(final CompleteToolCall completeToolCall) {
            completeToolCalls.add(completeToolCall);
        }
    }
}
