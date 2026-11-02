package org.killze.acgbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Param;
import org.killze.acgbox.dto.content.AnimePageDTO;
import org.killze.acgbox.entity.content.Anime;

import java.time.LocalDate;

public interface AnimeMapper extends BaseMapper<Anime> {
    /** PostgreSQL 事务级名称写锁，所有应用实例共用，事务结束自动释放。 */
    @org.apache.ibatis.annotations.Select("SELECT 1 FROM pg_advisory_xact_lock(1094928194, 0)")
    Long lockAnimeNames();

    /**
     * 分页查询动画
     *
     * @param page 分页参数
     * @param pageDTO 查询条件
     * @param startDate 转换后的开始日期
     * @param endDate 转换后的结束日期
     * @return 动画分页结果
     */
    IPage<Anime> selectAnimePage(
            Page<Anime> page,
            @Param("pageDTO") AnimePageDTO pageDTO,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
}
