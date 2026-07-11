package org.killze.acgbox.entity.content;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 通用公司关联实体，对应 company_relation 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("company_relation")
public class CompanyRelation {

    /**
     * 公司关联 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联对象类型，例如 ANIME、MANGA、NOVEL。
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 关联对象 ID。
     */
    @TableField("target_id")
    private Long targetId;

    /**
     * 公司 ID。
     */
    @TableField("company_id")
    private Long companyId;

    /**
     * 公司在对象中的职责，例如制作、发行、企划。
     */
    private String role;

    /**
     * 记录创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 记录最后更新时间。
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
