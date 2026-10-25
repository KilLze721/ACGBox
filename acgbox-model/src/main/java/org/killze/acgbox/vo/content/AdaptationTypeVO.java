package org.killze.acgbox.vo.content;

import lombok.Builder;
import lombok.Data;

/**
 * 改编类型视图对象
 */
@Data
@Builder
public class AdaptationTypeVO {

    /**
     * 改编类型 ID。
     */
    private Long id;

    /**
     * 改编类型名称，例如原创、漫画改、小说改、游戏改。
     */
    private String name;

    /**
     * 排序值，越小越靠前，同值时按 ID 升序。
     */
    private Long sortOrder;
}
