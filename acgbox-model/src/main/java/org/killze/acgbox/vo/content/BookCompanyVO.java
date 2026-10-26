package org.killze.acgbox.vo.content;

import lombok.Builder;
import lombok.Data;

/**
 * 书籍分页中的公司信息，额外包含公司当前名称。
 */
@Data
@Builder
public class BookCompanyVO {
    /**
     * 公司 ID。
     */
    private Long companyId;

    /**
     * 公司名称。
     */
    private String companyName;

    /**
     * 公司在原作中的职责。
     */
    private String role;

    /**
     * 公司关联排序值。
     */
    private Long sortOrder;
}
