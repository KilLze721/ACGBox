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
 * 通用人物关联实体，对应 person_relation 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("person_relation")
public class PersonRelation {

    /**
     * 人物关联 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联对象类型，数据库允许 ANIME、BOOK，本轮只写入 ANIME。
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 关联作品 ID。
     */
    @TableField("target_id")
    private Long targetId;

    /**
     * 创作人员 ID。
     */
    @TableField("person_id")
    private Long personId;

    /**
     * 人员在本作品中的职责，同一人员可以承担不同职责。
     */
    private String role;

    /**
     * 非负署名排序值，同值时按关联 ID 升序。
     */
    @TableField("sort_order")
    private Long sortOrder;
}
