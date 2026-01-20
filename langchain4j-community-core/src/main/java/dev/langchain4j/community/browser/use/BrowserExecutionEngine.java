package dev.langchain4j.community.browser.use;

/**
 * Represents a browser execution engine that can be used to perform actions on the browser in response to a user action.
 */
public interface BrowserExecutionEngine {

    /**
     * Navigate to a given url.
     * @param url URL to navigate page to.
     */
    void navigate(String url);

    /**
     * Click an element matching selector.
     * @param element A selector to search for an element.
     */
    void click(String element);

    /**
     * Reload the current page.
     */
    void reload();

    /**
     * Navigate to the previous page in history.
     */
    void goBack();

    /**
     * Navigate to the next page in history.
     */
    void goForward();

    /**
     * Return the page's title.
     * @return The title of the page.
     */
    String getTitle();

    /**
     * Get the full HTML contents of the page, including the doctype.
     * @return The HTML of the page.
     */
    String getHtml();

    /**
     * Get the text content of the page's body, not including the html tags.
     * @return The text of the page's body.
     */
    String getText();

    /**
     * Press the `Enter` key.
     */
    void pressEnter();

    /**
     * Wait for the given timeout in seconds.
     * @param seconds The seconds to wait for.
     */
    void waitForTimeout(Integer seconds);

    /**
     * Type the given text into a focused element.
     * @param text A text to type into a focused element.
     */
    void typeText(String text);

    /**
     * Fill the text to an element matching selector.
     * @param element  A selector to search for an element.
     * @param text Value to fill for the <input>, <textarea> or [contenteditable] element.
     */
    void inputText(String element, String text);

    /**
     * Drag the source element to the target element.
     * @param source A selector to search for an element to drag.
     * @param target A selector to search for an element to drop onto.
     */
    void dragAndDrop(String source, String target);
}
