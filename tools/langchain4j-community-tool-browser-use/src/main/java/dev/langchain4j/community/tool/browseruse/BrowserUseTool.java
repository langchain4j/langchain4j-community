package dev.langchain4j.community.tool.browseruse;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.community.browser.use.Action;
import dev.langchain4j.community.browser.use.BrowserExecutionEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A tool that executes browser actions using BrowserExecutionEngine.
 */
public class BrowserUseTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(BrowserUseTool.class);

    private final BrowserExecutionEngine engine;

    public BrowserUseTool(BrowserExecutionEngine engine) {
        this.engine = engine;
    }

    /**
     * Creates a new BrowserUseTool with the specified browser use engine.
     *
     * @param engine the browser use engine to use for browser using the web
     * @return a new BrowserUseTool
     */
    public static BrowserUseTool from(BrowserExecutionEngine engine) {
        return new BrowserUseTool(engine);
    }

    @Tool(
            name = "browser_use",
            value =
                    """
                            A browser automation tool that allows interaction with a web browser to perform various actions.
                            Use this when you need to browse websites, fill forms, click buttons, or extract content, etc.
                            Each action requires specific parameters as the following: {
                            'NAVIGATE': ['url'],
                            'CLICK': ['element'],
                            'RELOAD': [],
                            'GO_BACK': [],
                            'GO_FORWARD': [],
                            'GET_TITLE': [],
                            'GET_HTML': [],
                            'GET_TEXT': [],
                            'PRESS_ENTER': [],
                            'WAIT': ['seconds'],
                            'TYPE_TEXT': ['text'],
                            'INPUT_TEXT': ['element', 'text'],
                            'DRAG_DROP': ['source', 'target'],
                            }
                            """)
    public String execute(
            @P(
                            """
                            The browser action to perform. Supported actions: [
                            - 'NAVIGATE': Go to a specific URL.
                            - 'CLICK': Click an element by a XPath / CSS selector.
                            - 'RELOAD': Reload / refresh the current page.
                            - 'GO_BACK': Navigate to the previous page in history.
                            - 'GO_FORWARD': Navigate to the next page in history.
                            - 'GET_TITLE': Return the page's title.
                            - 'GET_HTML': Get HTML content of the page.
                            - 'GET_TEXT': Get text content of the page.
                            - 'PRESS_ENTER': Hit the Enter key.
                            - 'WAIT': Wait for some seconds.
                            - 'TYPE_TEXT': Type text into a focused element.
                            - 'INPUT_TEXT': Input text into an element.
                            - 'DRAG_DROP': Drag the source element to the target element.
                            ]
                            """)
                    Action action,
            @P(value = "URL for 'NAVIGATE' action", required = false) String url,
            @P(value = "Element(XPath / CSS selector) for 'CLICK' or 'INPUT_TEXT' actions", required = false)
                    String element,
            @P(value = "Text for 'TYPE_TEXT' or 'INPUT_TEXT' actions", required = false) String text,
            @P(value = "Seconds to wait for 'WAIT' action", required = false) Integer seconds,
            @P(value = "Source element(XPath / CSS selector) for 'DRAG_DROP' action", required = false) String source,
            @P(value = "Target element(XPath / CSS selector) for 'DRAG_DROP' action", required = false) String target) {

        LOGGER.debug("Perform action:{}", action);

        String ret = "Action '" + action + "' executed successfully";
        switch (action) {
            case NAVIGATE:
                engine.navigate(url);
                break;
            case CLICK:
                engine.click(element);
                break;
            case RELOAD:
                engine.reload();
                break;
            case GO_BACK:
                engine.goBack();
                break;
            case GO_FORWARD:
                engine.goForward();
                break;
            case GET_TITLE:
                ret = engine.getTitle();
                break;
            case GET_HTML:
                ret = engine.getHtml();
                break;
            case GET_TEXT:
                ret = engine.getText();
                break;
            case PRESS_ENTER:
                engine.pressEnter();
                break;
            case WAIT:
                engine.waitForTimeout(seconds);
                break;
            case TYPE_TEXT:
                engine.typeText(text);
                break;
            case INPUT_TEXT:
                engine.inputText(element, text);
                break;
            case DRAG_DROP:
                engine.dragAndDrop(source, target);
                break;
            default:
                throw new IllegalArgumentException("Unknown action: " + action);
        }
        return ret;
    }
}
