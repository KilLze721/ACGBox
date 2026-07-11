package org.killze.acgbox.entity.content;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 个人评分实体，对应 personal_rating 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("personal_rating")
public class PersonalRating {

    /**
     * 个人评分 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 评分对象类型，例如 ANIME、MANGA、NOVEL。
     */
    @TableField("target_type")
    private String targetType;

    /**
     * 评分对象 ID。
     */
    @TableField("target_id")
    private Long targetId;

    /**
     * 个人评分，范围 0 到 10，支持一位小数。
     */
    private BigDecimal score;

    /**
     * 创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 最后修改时间。
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
