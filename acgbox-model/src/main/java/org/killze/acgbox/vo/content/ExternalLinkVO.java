package org.killze.acgbox.vo.content;

import lombok.Builder;
import lombok.Data;

/**
 * 外部链接视图对象
 */
@Data
@Builder
public class ExternalLinkVO {

    /**
     * 链接标题，例如官网、Bangumi、维基百科
     */
    private String title;

    /**
     * 链接 URL
     */
    private String url;

    /**
     * 排序值，越小越靠前
     */
    private Long sortOrder;
}
