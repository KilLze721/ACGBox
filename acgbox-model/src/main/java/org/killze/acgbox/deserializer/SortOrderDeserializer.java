package org.killze.acgbox.deserializer;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * 排序字段只接收整数 JSON 数字，禁止小数截断和字符串等类型的隐式转换。
 */
public class SortOrderDeserializer extends ValueDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) {
        if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
            return context.reportInputMismatch(Long.class, "排序值必须为整数 JSON 数字");
        }
        return parser.getLongValue();
    }
}
