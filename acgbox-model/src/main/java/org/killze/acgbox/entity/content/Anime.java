package org.killze.acgbox.entity.content;

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
public class Anime {

    /**
     * 动画 ID。
     */
    private Integer id;

    /**
     * 动画名称。
     */
    private String name;

    /**
     * 动画总集数。
     */
    private Integer episodeCount;

    /**
     * 放送类型 ID，关联 broadcast_type 表。
     */
    private Integer broadcastTypeId;

    /**
     * 改编类型 ID，关联 adaptation_type 表。
     */
    private Integer adaptationTypeId;

    /**
     * 动画开播日期。
     */
    private LocalDate airDate;

    /**
     * 封面图片 URL。
     */
    private String coverImageUrl;

    /**
     * 放送状态，例如放送中、已完结。
     */
    private String status;

    /**
     * 记录创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 记录最后更新时间。
     */
    private LocalDateTime updatedAt;
}
