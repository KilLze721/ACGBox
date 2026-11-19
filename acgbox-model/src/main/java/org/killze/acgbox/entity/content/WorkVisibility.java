package org.killze.acgbox.entity.content;

import lombok.Data;

/** 作品公开状态，对应独立表 work_visibility 的联合主键记录。 */
@Data
public class WorkVisibility {
    /** 作品类型，ANIME 或 BOOK。 */
    private String targetType;
    /** 对应类型的真实作品 ID。 */
    private Long targetId;
    /** 该作品是否公开展示。 */
    private Boolean isPublic;
}
