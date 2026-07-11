package org.killze.acgbox.dto.content;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 外部链接数据传输对象
 */
@Data
public class ExternalLinkDTO {

    /**
     * 链接标题，例如官网、Bangumi、维基百科
     */
    private String title;

    /**
     * 链接 URL
     */
    @NotBlank(message = "链接 URL 不能为空")
    private String url;

    /**
     * 排序值，越小越靠前
     */
    private Long sortOrder;
}
