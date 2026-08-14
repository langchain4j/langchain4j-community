package dev.langchain4j.community.browser.use;

/**
 * Supported browser actions.
 */
public enum Action {
    /**
     * Navigate to a given url.
     */
    NAVIGATE,

    /**
     * Click an element matching selector.
     */
    CLICK,

    /**
     * Reload the current page.
     */
    RELOAD,

    /**
     * Navigate to the previous page in history.
     */
    GO_BACK,

    /**
     * Navigate to the next page in history.
     */
    GO_FORWARD,

    /**
     * Return the page's title.
     */
    GET_TITLE,

    /**
     *  Get the full HTML contents of the page, including the doctype.
     */
    GET_HTML,

    /**
     * Get the text content of the page's body, not including the html tags.
     */
    GET_TEXT,

    /**
     * Press the `Enter` key.
     */
    PRESS_ENTER,

    /**
     * Wait for the given timeout in seconds.
     */
    WAIT,

    /**
     * Type the given text into a focused element.
     */
    TYPE_TEXT,

    /**
     * Fill the text to an element matching selector.
     */
    INPUT_TEXT,

    /**
     * Drag the source element to the target element.
     */
    DRAG_DROP
}
