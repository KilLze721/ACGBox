package org.killze.acgbox.vo.content;

import lombok.Builder;
import lombok.Data;

/**
 * 公司视图对象
 */
@Data
@Builder
public class CompanyVO {

    /**
     * 公司 ID
     */
    private Long id;

    /**
     * 公司名称
     */
    private String name;

    /**
     * 公司简介
     */
    private String description;
}
