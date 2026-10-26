package org.killze.acgbox.deserializer;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * 发表日期仅接收真实完整日期，不将年份或年月自动补齐。
 */
public class BookDateDeserializer extends ValueDeserializer<LocalDate> {
    @Override
    public LocalDate deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.hasToken(JsonToken.VALUE_STRING) && parser.getString().matches("\\d{4}-\\d{2}-\\d{2}")) {
            try {
                LocalDate date = LocalDate.parse(parser.getString());
                if (date.getYear() > 0) {
                    return date;
                }
            } catch (DateTimeParseException exception) {
                return context.reportInputMismatch(LocalDate.class, "发表日期必须是有效完整日期 yyyy-MM-dd");
            }
        }
        return context.reportInputMismatch(LocalDate.class, "发表日期必须是有效完整日期 yyyy-MM-dd");
    }
}
