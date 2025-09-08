package dev.langchain4j.community.xinference.spring;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Objects;

public class XinferenceProxyProperties {
    private Proxy.Type type;
    private String host;
    private Integer port;

    public Proxy.Type getType() {
        return type;
    }

    public void setType(Proxy.Type type) {
        this.type = type;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public static Proxy convert(XinferenceProxyProperties properties) {
        if (Objects.isNull(properties)) {
            return null;
        }
        return new Proxy(properties.getType(), new InetSocketAddress(properties.getHost(), properties.getPort()));
    }
}
