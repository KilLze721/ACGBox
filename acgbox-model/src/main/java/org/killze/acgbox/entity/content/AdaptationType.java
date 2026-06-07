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
 * 改编类型实体，对应 adaptation_type 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("adaptation_type")
public class AdaptationType {

    /**
     * 改编类型 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 改编类型名称，例如原创、漫画改、小说改、游戏改。
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
