package dev.langchain4j.community.browser.use;

class MockBrowserExecutionEngine implements BrowserExecutionEngine {

    @Override
    public void navigate(final String url) {}

    @Override
    public void click(final String element) {}

    @Override
    public void reload() {}

    @Override
    public void goBack() {}

    @Override
    public void goForward() {}

    @Override
    public String getTitle() {
        return "LangChain4j";
    }

    @Override
    public String getHtml() {
        return "<h1>Supercharge your Java application with the power of LLMs</h1>";
    }

    @Override
    public String getText() {
        return "Supercharge your Java application with the power of LLMs";
    }

    @Override
    public void pressEnter() {}

    @Override
    public void waitForTimeout(final Integer seconds) {}

    @Override
    public void typeText(final String text) {}

    @Override
    public void inputText(final String element, final String text) {}

    @Override
    public void dragAndDrop(final String source, final String target) {}
}
