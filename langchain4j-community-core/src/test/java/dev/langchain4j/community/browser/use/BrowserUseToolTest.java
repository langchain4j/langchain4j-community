package dev.langchain4j.community.browser.use;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class BrowserUseToolTest {
    @Test
    void test_no_feedback_op() {
        final MockBrowserExecutionEngine mockBrowserExecutionEngine = spy(new MockBrowserExecutionEngine());
        BrowserUseTool tool = BrowserUseTool.from(mockBrowserExecutionEngine);
        tool.execute(Action.NAVIGATE, "", null, null, null, null, null);
        tool.execute(Action.CLICK, "", null, null, null, null, null);
        tool.execute(Action.RELOAD, "", null, null, null, null, null);
        tool.execute(Action.GO_BACK, "", null, null, null, null, null);
        tool.execute(Action.GO_FORWARD, "", null, null, null, null, null);
        tool.execute(Action.PRESS_ENTER, "", null, null, null, null, null);
        tool.execute(Action.WAIT, "", null, null, null, null, null);
        tool.execute(Action.TYPE_TEXT, "", null, null, null, null, null);
        tool.execute(Action.INPUT_TEXT, "", null, null, null, null, null);
        tool.execute(Action.DRAG_DROP, "", null, null, null, null, null);
        verify(mockBrowserExecutionEngine).navigate("");
        verify(mockBrowserExecutionEngine).click(null);
        verify(mockBrowserExecutionEngine).reload();
        verify(mockBrowserExecutionEngine).goBack();
        verify(mockBrowserExecutionEngine).goForward();
        verify(mockBrowserExecutionEngine).pressEnter();
        verify(mockBrowserExecutionEngine).waitForTimeout(null);
        verify(mockBrowserExecutionEngine).typeText(null);
        verify(mockBrowserExecutionEngine).inputText(null, null);
        verify(mockBrowserExecutionEngine).dragAndDrop(null, null);
    }

    @Test
    void test_getTitle() {
        final MockBrowserExecutionEngine mockBrowserExecutionEngine = spy(new MockBrowserExecutionEngine());
        BrowserUseTool tool = BrowserUseTool.from(mockBrowserExecutionEngine);
        final String ret = tool.execute(Action.GET_TITLE, "", null, null, null, null, null);
        assertEquals("LangChain4j", ret);
        verify(mockBrowserExecutionEngine).getTitle();
    }

    @Test
    void test_getHtml() {
        final MockBrowserExecutionEngine mockBrowserExecutionEngine = spy(new MockBrowserExecutionEngine());
        BrowserUseTool tool = BrowserUseTool.from(mockBrowserExecutionEngine);
        final String ret = tool.execute(Action.GET_HTML, "", null, null, null, null, null);
        assertEquals("<h1>Supercharge your Java application with the power of LLMs</h1>", ret);
        verify(mockBrowserExecutionEngine).getHtml();
    }

    @Test
    void test_getText() {
        final MockBrowserExecutionEngine mockBrowserExecutionEngine = spy(new MockBrowserExecutionEngine());
        BrowserUseTool tool = BrowserUseTool.from(mockBrowserExecutionEngine);
        final String ret = tool.execute(Action.GET_TEXT, "", null, null, null, null, null);
        assertEquals("Supercharge your Java application with the power of LLMs", ret);
        verify(mockBrowserExecutionEngine).getText();
    }
}
