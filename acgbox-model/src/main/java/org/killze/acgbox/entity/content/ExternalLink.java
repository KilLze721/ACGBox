package org.killze.acgbox.entity.content;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 外部链接实体，对应 external_link 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExternalLink {

    /**
     * 链接 ID。
     */
    private Integer id;

    /**
     * 动画 ID，关联 anime 表。
     */
    private Integer animeId;

    /**
     * 链接标题，例如官网、Bangumi、维基百科。
     */
    private String title;

    /**
     * 链接 URL。
     */
    private String url;
}
