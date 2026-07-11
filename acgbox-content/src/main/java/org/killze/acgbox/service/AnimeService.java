package org.killze.acgbox.service;

import jakarta.validation.Valid;
import org.killze.acgbox.dto.content.AnimeDTO;
import org.killze.acgbox.vo.content.AnimeVO;

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
     * 根据 ID 查询动画详情
     *
     * @param id 动画 ID
     * @return 动画详情
     */
    AnimeVO getAnimeById(Long id);
}
