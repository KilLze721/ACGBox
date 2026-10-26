package org.killze.acgbox.dto.content;

import org.killze.acgbox.deserializer.IntegerValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * 书籍请求复用现有关联字段，仅增强本请求的整数绑定，不改变动画契约。
 */
public class BookPersonDTO extends PersonRelationDTO {

    @Override
    @JsonDeserialize(using = IntegerValueDeserializer.class)
    public void setPersonId(Long value) {
        super.setPersonId(value);
    }
}
