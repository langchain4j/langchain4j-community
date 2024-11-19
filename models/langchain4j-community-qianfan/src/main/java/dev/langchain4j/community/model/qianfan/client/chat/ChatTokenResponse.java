package dev.langchain4j.community.model.qianfan.client.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies.SnakeCaseStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(NON_NULL)
@JsonNaming(SnakeCaseStrategy.class)
public class ChatTokenResponse {

    private String refreshToken;
    private Integer expiresIn;
    private String sessionKey;
    private String accessToken;
    private String scope;
    private String sessionSecret;

    public ChatTokenResponse() {

    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(final String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public Integer getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(final Integer expiresIn) {
        this.expiresIn = expiresIn;
    }

    public String getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(final String sessionKey) {
        this.sessionKey = sessionKey;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(final String accessToken) {
        this.accessToken = accessToken;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(final String scope) {
        this.scope = scope;
    }

    public String getSessionSecret() {
        return sessionSecret;
    }

    public void setSessionSecret(final String sessionSecret) {
        this.sessionSecret = sessionSecret;
    }

    @Override
    public String toString() {
        return "ChatTokenResponse{" +
                "refreshToken='" + refreshToken + '\'' +
                ", expiresIn=" + expiresIn +
                ", sessionKey='" + sessionKey + '\'' +
                ", accessToken='" + accessToken + '\'' +
                ", scope='" + scope + '\'' +
                ", sessionSecret='" + sessionSecret + '\'' +
                '}';
    }
}
