package org.killze.acgbox.web.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.killze.acgbox.dto.common.PageDTO;

import java.util.List;

/** 公开目录只接受展示筛选项，不接收管理端可见性或评分参数。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PublicCatalogPageDTO extends PageDTO {
    private String keyword;
    private Long broadcastTypeId;
    private Long bookTypeId;
    private Long adaptationTypeId;
    private Long status;
    private String year;
    private List<Long> tagIds;
    private String sortBy;
    private String sortDirection;
}
