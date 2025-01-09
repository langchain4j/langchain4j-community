package dev.langchain4j.community.model.zhipu.assistant;

import java.util.Objects;

/**
 * 封装返回结果
 * @param <T>
 */
public interface ClientResponse<T> {

    T getData();

    void setData(T data);

    void setCode(int code);

    int getCode();

    void setMessage(String message);

    default boolean isSuccess() {
        return Objects.equals(getCode(), 200);
    }
}
