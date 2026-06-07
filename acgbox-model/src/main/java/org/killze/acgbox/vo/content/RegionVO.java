package org.killze.acgbox.vo.content;

import lombok.Builder;
import lombok.Data;

/**
 * 地区视图对象。
 */
@Data
@Builder
public class RegionVO  {

    private Long id;

    private String name;
}
