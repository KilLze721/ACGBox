package org.killze.acgbox.entity.content;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 标签实体，对应 tag 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tag {

    /**
     * 标签 ID。
     */
    private Integer id;

    /**
     * 标签名称，例如科幻、恋爱、奇幻。
     */
    private String name;
}
