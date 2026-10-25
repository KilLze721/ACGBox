package org.killze.acgbox.vo.content;

import lombok.Builder;
import lombok.Data;

/**
 * 动画人物关联视图对象，姓名从当前人物档案读取。
 */
@Data
@Builder
public class PersonRelationVO {

    /**
     * 人物 ID。
     */
    private Long personId;

    /**
     * 人物当前姓名或署名。
     */
    private String personName;

    /**
     * 人物在本作品中的职责。
     */
    private String role;

    /**
     * 非负署名排序值，同值时按关联 ID 升序。
     */
    private Long sortOrder;
}
