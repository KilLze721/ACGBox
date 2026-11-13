package org.killze.acgbox.vo.content;

import org.killze.acgbox.vo.common.PageVO;

import java.time.LocalDate;

/**
 * 作品详情中展示的系列及其关联作品分页。
 *
 * @param series 系列基本信息
 * @param works 关联作品分页
 */
public record SeriesWorksVO(SeriesVO series, PageVO<Work> works) {

    /**
     * 用于展示及跳转的作品摘要。
     *
     * @param workType 作品类型，ANIME 或 BOOK
     * @param workId 对应类型的作品 ID
     * @param name 作品名称
     * @param coverImageUrl 封面地址，可为空
     * @param releaseDate 动画开播日期或书籍发表日期，可为空
     * @param bookTypeName 书籍类型名称，动画为 null
     */
    public record Work(String workType, Long workId, String name, String coverImageUrl,
                       LocalDate releaseDate, String bookTypeName) { }
}
