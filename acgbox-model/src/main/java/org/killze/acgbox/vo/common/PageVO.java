package org.killze.acgbox.vo.common;


import lombok.Builder;
import lombok.Data;


import java.util.List;

/**
 * 分页结果封装类
 */
@Data
@Builder
public class PageVO<T> {

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 当前页数据
     */
    private List<T> rows;
}
