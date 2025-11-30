package dev.langchain4j.community.browser.use;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;

public class PlaywrightBrowserExecutionEngine implements BrowserExecutionEngine {

    private final Browser browser;
    private final Page page;

    public PlaywrightBrowserExecutionEngine() {
        this(true, "chrome", true, 500);
    }

    public PlaywrightBrowserExecutionEngine(boolean headless, String channel, boolean sandbox, double slowMo) {
        Playwright playwright = Playwright.create();
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setChannel(channel)
                .setChromiumSandbox(sandbox)
                .setSlowMo(slowMo);
        this.browser = playwright.chromium().launch(options);
        this.page = browser.newPage();
    }

    public PlaywrightBrowserExecutionEngine(Browser browser) {
        this.browser = browser;
        this.page = browser.newPage();
    }

    @Override
    public void navigate(final String url) {
        Page.NavigateOptions options = new Page.NavigateOptions();
        options.setWaitUntil(WaitUntilState.DOMCONTENTLOADED);
        page.navigate(url, options);
    }

    @Override
    public void click(final String element) {
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
    public void waitForTimeout(final Integer seconds) {
        page.waitForTimeout(seconds * 1000.0);
    }

    @Override
    public void pressEnter() {
        page.keyboard().press("Enter");
    }

    @Override
    public void typeText(final String text) {
        page.keyboard().type(text);
    }

    @Override
    public void inputText(final String element, final String text) {
        page.fill(element, text);
    }

    @Override
    public void dragAndDrop(final String source, final String target) {
        page.dragAndDrop(source, target);
    }

    @Override
    public void close() {
        if (page != null) {
            page.close();
        }
        if (browser != null) {
            browser.close();
        }
    }
}
