package org.killze.acgbox.entity.content;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 放送类型实体，对应 broadcast_type 表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BroadcastType {

    /**
     * 放送类型 ID。
     */
    private Integer id;

    /**
     * 放送类型名称，例如 TV、WEB、OVA、剧场版。
     */
    private String name;
}
