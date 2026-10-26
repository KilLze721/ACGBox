package org.killze.acgbox.deserializer;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * 新书籍输入专用整数解析，不接受小数截断或字符串隐式转换。
 */
public class IntegerValueDeserializer extends ValueDeserializer<Long> {
    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return context.reportInputMismatch(Long.class, "数值必须为整数 JSON 数字");
        }
        return parser.getLongValue();
    }
}
