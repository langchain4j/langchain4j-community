package dev.langchain4j.community.xinference.spring;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Objects;

public class ProxyProperties {
    private Proxy.Type type;
    private String host;
    private Integer port;

    public Proxy.Type getType() {
        return type;
    }

    public void setType(final Proxy.Type type) {
        this.type = type;
    }

    public String getHost() {
        return host;
    }

    public void setHost(final String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(final Integer port) {
        this.port = port;
    }

    public static Proxy convert(ProxyProperties properties) {
        if (Objects.isNull(properties)) {
            return null;
        }
        return new Proxy(properties.getType(), new InetSocketAddress(properties.getHost(), properties.getPort()));
    }
}
