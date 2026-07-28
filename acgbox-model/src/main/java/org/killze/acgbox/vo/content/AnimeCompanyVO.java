package org.killze.acgbox.vo.content;

import lombok.Builder;
import lombok.Data;

/**
 * 动画关联公司视图对象
 */
@Data
@Builder
public class AnimeCompanyVO {

    /**
     * 公司 ID
     */
    private Long companyId;

    /**
     * 公司名称
     */
    private String companyName;

    /**
     * 公司在动画中的职责
     */
    private String role;
}
