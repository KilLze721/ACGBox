package org.killze.acgbox.vo.content;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人物资料视图对象。
 */
@Data
@Builder
public class PersonVO {

    /**
     * 人物 ID，用于区分同名人员。
     */
    private Long id;

    /**
     * 人物姓名或署名。
     */
    private String name;

    /**
     * 人物简介。
     */
    private String description;

    /**
     * 记录创建时间，只读。
     */
    private LocalDateTime createdAt;

    /**
     * 记录最后更新时间，只读。
     */
    private LocalDateTime updatedAt;
}
