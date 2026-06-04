package org.killze.acgbox.entity.content;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 动画实体，对应 anime 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("anime")
public class Anime {

    /**
     * 动画 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 动画名称。
     */
    private String name;

    /**
     * 动画总集数。
     */
    @TableField("episode_count")
    private Long episodeCount;

    /**
     * 放送类型 ID，关联 broadcast_type 表。
     */
    @TableField("broadcast_type_id")
    private Long broadcastTypeId;

    /**
     * 改编类型 ID，关联 adaptation_type 表。
     */
    @TableField("adaptation_type_id")
    private Long adaptationTypeId;

    /**
     * 动画开播日期。
     */
    @TableField("air_date")
    private LocalDate airDate;

    /**
     * 封面图片 URL。
     */
    @TableField("cover_image_url")
    private String coverImageUrl;

    /**
     * 放送状态，例如放送中、已完结。
     */
    private String status;

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
