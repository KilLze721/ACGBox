package org.killze.acgbox.entity.content;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 改编类型实体，对应 adaptation_type 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdaptationType {

    /**
     * 改编类型 ID。
     */
    private Integer id;

    /**
     * 改编类型名称，例如原创、漫画改、小说改、游戏改。
     */
    private String name;
}
