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
 * 地区实体，对应 region 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("region")
public class Region {
    /**
     * 地区 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 地区名称。
     */
    private String name;

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
