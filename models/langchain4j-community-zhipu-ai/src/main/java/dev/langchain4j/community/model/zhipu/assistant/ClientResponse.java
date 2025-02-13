package dev.langchain4j.community.model.zhipu.assistant;

import java.util.Objects;

public interface ClientResponse<T> {

    T getData();

    void setData(T data);

    void setCode(Integer code);

    Integer getCode();

    void setMessage(String message);

    default boolean isSuccess() {
        return Objects.equals(getCode(), 200);
    }
}
