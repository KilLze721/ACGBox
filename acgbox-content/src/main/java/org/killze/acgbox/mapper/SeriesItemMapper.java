package org.killze.acgbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.killze.acgbox.entity.content.SeriesItem;
import org.killze.acgbox.vo.content.SeriesWorksVO.Work;

public interface SeriesItemMapper extends BaseMapper<SeriesItem> {

    /** 分页读取系列中仍存在的动画和书籍，不加载作品详情。 */
    Page<Work> selectSeriesWorks(Page<Work> page, @Param("seriesId") Long seriesId);
}
