package dev.langchain4j.community.browser.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import dev.langchain4j.community.browser.use.BrowserExecutionEngine;

/**
 * An implementation of a {@link BrowserExecutionEngine} that uses
 * <a href="https://playwright.dev/java/">Playwright Java API</a> for performing browser actions.
 */
public class PlaywrightBrowserExecutionEngine implements BrowserExecutionEngine {

    private final Browser browser;
    private final Page page;

    public PlaywrightBrowserExecutionEngine(Browser browser) {
        this.browser = browser;
        // Currently only keep one page
        this.page = browser.newPage();
    }

    @Override
    public void navigate(String url) {
        Page.NavigateOptions options = new Page.NavigateOptions();
        options.setWaitUntil(WaitUntilState.DOMCONTENTLOADED);
        page.navigate(url, options);
    }

    @Override
    public void click(String element) {
        page.click(element);
    }

    @Override
    public void reload() {
        page.reload();
    }

    @Override
    public void goBack() {
        page.goBack();
    }

    @Override
    public void goForward() {
        page.goForward();
    }

    @Override
    public String getTitle() {
        return page.title();
    }

    @Override
    public String getHtml() {
        return page.content();
    }

    @Override
    public String getText() {
        return page.locator("body").innerText();
    }

    @Override
    public void waitForTimeout(Integer seconds) {
        page.waitForTimeout(seconds * 1000.0);
    }

    @Override
    public void pressEnter() {
        page.keyboard().press("Enter");
    }

    @Override
    public void typeText(String text) {
        page.keyboard().type(text);
    }

    @Override
    public void inputText(String element, String text) {
        page.fill(element, text);
    }

    @Override
    public void dragAndDrop(String source, String target) {
        page.dragAndDrop(source, target);
    }

    public static PlaywrightBrowserExecutionEngineBuilder builder() {
        return new PlaywrightBrowserExecutionEngineBuilder();
    }

    public static class PlaywrightBrowserExecutionEngineBuilder {
        private Browser browser;

        PlaywrightBrowserExecutionEngineBuilder() {}

        public PlaywrightBrowserExecutionEngineBuilder browser(Browser browser) {
            this.browser = browser;
            return this;
        }

        public PlaywrightBrowserExecutionEngine build() {
            return new PlaywrightBrowserExecutionEngine(browser);
        }
    }
}
