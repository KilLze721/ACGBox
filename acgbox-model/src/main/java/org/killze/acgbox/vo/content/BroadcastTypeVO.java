package org.killze.acgbox.vo.content;

import lombok.Builder;
import lombok.Data;

/**
 * 放送类型视图对象
 */
@Data
@Builder
public class BroadcastTypeVO {

    /**
     * 放送类型 ID。
     */
    private Long id;

    /**
     * 放送类型名称，例如 TV、WEB、OVA、剧场版。
     */
    private String name;
}
