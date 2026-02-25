package dev.langchain4j.community.tool.browseruse;

import dev.langchain4j.community.browser.use.BrowserExecutionEngine;

class MockBrowserExecutionEngine implements BrowserExecutionEngine {

    @Override
    public void navigate(String url) {}

    @Override
    public void click(String element) {}

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
    public void waitForTimeout(Integer seconds) {}

    @Override
    public void typeText(String text) {}

    @Override
    public void inputText(String element, String text) {}

    @Override
    public void dragAndDrop(String source, String target) {}
}
