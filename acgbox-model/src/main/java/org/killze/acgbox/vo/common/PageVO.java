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
     * 当前页码
     */
    private Long pageNum;

    /**
     * 每页条数
     */
    private Long pageSize;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 总页数
     */
    private Long pages;

    /**
     * 当前页数据
     */
    private List<T> rows;
}
