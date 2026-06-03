package org.killze.acgbox.entity.content;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 公司实体，对应 company 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Company {

    /**
     * 公司 ID。
     */
    private Integer id;

    /**
     * 公司名称。
     */
    private String name;
}
