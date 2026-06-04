package org.killze.acgbox.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一返回结果
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /**
     * 状态码
     */
    private Integer code;

    /**
     * 消息
     */
    private String message;

    /**
     * 数据
     */
    private T data;

    /**
     * 成功（无数据）
     */
    public static <T> Result<T> success() {

        return Result.<T>builder()
                .code(ResultCode.SUCCESS.getCode())
                .message(ResultCode.SUCCESS.getMessage())
                .build();
    }

    /**
     * 成功（有数据）
     */
    public static <T> Result<T> success(T data) {

        return Result.<T>builder()
                .code(ResultCode.SUCCESS.getCode())
                .message(ResultCode.SUCCESS.getMessage())
                .data(data)
                .build();
    }

    /**
     * 失败
     */
    public static <T> Result<T> error(String message) {

        return Result.<T>builder()
                .code(ResultCode.ERROR.getCode())
                .message(message)
                .build();
    }

    /**
     * 自定义失败
     */
    public static <T> Result<T> error(ResultCode resultCode) {

        return Result.<T>builder()
                .code(resultCode.getCode())
                .message(resultCode.getMessage())
                .build();
    }
}

