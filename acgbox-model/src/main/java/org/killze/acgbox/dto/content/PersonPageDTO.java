package org.killze.acgbox.dto.content;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.killze.acgbox.dto.common.PageDTO;

/**
 * 人物分页查询参数，页码及每页条数由人物服务单独校验。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PersonPageDTO extends PageDTO {

    /**
     * 人物姓名，忽略字母大小写进行模糊查询。
     */
    private String name;
}
