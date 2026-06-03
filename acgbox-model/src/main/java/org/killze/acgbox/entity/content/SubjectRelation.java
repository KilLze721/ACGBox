package org.killze.acgbox.entity.content;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("subject_relation")
public class SubjectRelation {

    /**
     * 关联 ID。
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 源作品类型，例如 anime、manga、novel。
     */
    @TableField("source_type")
    private String sourceType;

    /**
     * 源作品 ID。
     */
    @TableField("source_id")
    private Integer sourceId;

    /**
     * 目标作品类型，例如 anime、manga、novel。
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 目标作品 ID。
     */
    @TableField("target_id")
    private Integer targetId;

    /**
     * 关联类型，例如前传、续作、改编自。
     */
    @TableField("relation_type")
    private String relationType;
}
