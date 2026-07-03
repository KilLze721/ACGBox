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
 * 系列实体，对应 series 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("series")
public class Series {

    /**
     * 系列 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 系列名称。
     */
    private String name;

    /**
     * 系列简介。
     */
    private String description;

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
