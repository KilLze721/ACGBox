package org.killze.acgbox.service;

import jakarta.validation.Valid;
import org.killze.acgbox.dto.content.AnimeDTO;
import org.killze.acgbox.dto.content.AnimePageDTO;
import org.killze.acgbox.vo.common.PageVO;
import org.killze.acgbox.vo.content.AnimePageVO;
import org.killze.acgbox.vo.content.AnimeVO;

import java.util.List;

/**
 * 动画服务接口
 *
 * @author killze
 */
public interface AnimeService {

    /**
     * 创建动画
     *
     * @param animeDTO 动画信息
     * @return 动画信息
     */
    AnimeVO createAnime(@Valid AnimeDTO animeDTO);

    /**
     * 修改动画
     *
     * @param animeDTO 动画信息
     * @return 动画信息
     */
    AnimeVO updateAnime(@Valid AnimeDTO animeDTO);

    /**
     * 批量删除动画
     *
     * @param ids 动画 ID 列表
     */
    void deleteAnime(List<Long> ids);

    /**
     * 分页查询动画
     *
     * @param pageDTO 分页查询参数
     * @return 动画分页结果
     */
    PageVO<AnimePageVO> pageAnime(@Valid AnimePageDTO pageDTO);

    /**
     * 根据 ID 查询动画详情
     *
     * @param id 动画 ID
     * @return 动画详情
     */
    AnimeVO getAnimeById(Long id);
}
