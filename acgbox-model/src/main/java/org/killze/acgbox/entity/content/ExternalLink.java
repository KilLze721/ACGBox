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
 * 外部链接实体，对应 external_link 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("external_link")
public class ExternalLink {

    /**
     * 链接 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 动画 ID，关联 anime 表。
     */
    @TableField("anime_id")
    private Long animeId;

    /**
     * 链接标题，例如官网、Bangumi、维基百科。
     */
    private String title;

    /**
     * 链接 URL。
     */
    private String url;
}
