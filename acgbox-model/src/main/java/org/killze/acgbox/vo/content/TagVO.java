package org.killze.acgbox.vo.content;

import lombok.Builder;
import lombok.Data;

/**
 * 标签视图对象。
 */
@Data
@Builder
public class TagVO {

    /**
     * 标签 ID
     */
    private Long id;

    /**
     * 标签名称
     */
    private String name;
}
