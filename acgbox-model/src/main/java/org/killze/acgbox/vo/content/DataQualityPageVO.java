package org.killze.acgbox.vo.content;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** 数据质量分页、当前范围统计及检查明细。 */
public record DataQualityPageVO(Long pageNum, Long pageSize, Long total, Long pages, OffsetDateTime checkedAt,
                                Summary summary, List<Row> rows) {

    public record Summary(Long scopeTotal, Map<String, Long> ruleCounts) { }

    public record Reference(Long id, String name) { }

    public record Issue(String code, String message) { }

    public record Row(String targetType, Long targetId, String name, String coverImageUrl,
                      Reference contentType, Reference region, LocalDateTime updatedAt,
                      List<Issue> issues, Long duplicateCount) { }
}
