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
 * 创作人员实体，对应 person 表，允许同名人员独立建档。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("person")
public class Person {

    /**
     * 人物 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 常用署名或笔名。
     */
    private String name;

    /**
     * 人物简介，可用于区分同名人员。
     */
    private String description;

    /**
     * 记录创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 记录最后更新时间，由应用维护。
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
