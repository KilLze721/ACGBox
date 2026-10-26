package org.killze.acgbox.dto.content;

import org.killze.acgbox.deserializer.IntegerValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.util.ArrayList;

/**
 * 书籍及类型删除仍接收 JSON 数组，仅对数组元素启用严格整数解析。
 */
@JsonDeserialize(contentUsing = IntegerValueDeserializer.class)
public class BookIdsDTO extends ArrayList<Long> {
}
