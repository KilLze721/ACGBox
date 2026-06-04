package org.killze.acgbox.result;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),

    BAD_REQUEST(400, "请求参数错误"),

    UNAUTHORIZED(401, "未登录"),

    FORBIDDEN(403, "无权限"),

    NOT_FOUND(404, "资源不存在"),

    ERROR(500, "服务器内部错误");

    private final Integer code;

    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}