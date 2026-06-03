package org.killze.acgbox.entity.content;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 作品关联实体，对应 subject_relation 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectRelation {

    /**
     * 关联 ID。
     */
    private Integer id;

    /**
     * 源作品类型，例如 anime、manga、novel。
     */
    private String sourceType;

    /**
     * 源作品 ID。
     */
    private Integer sourceId;

    /**
     * 目标作品类型，例如 anime、manga、novel。
     */
    private String targetType;

    /**
     * 目标作品 ID。
     */
    private Integer targetId;

    /**
     * 关联类型，例如前传、续作、改编自。
     */
    private String relationType;
}
