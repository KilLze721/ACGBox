package org.killze.acgbox.dto.content;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.killze.acgbox.dto.common.PageDTO;

import java.util.List;

/** 数据质量检查条件。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DataQualityPageDTO extends PageDTO {
    private String targetType;
    private String keyword;
    private List<String> ruleCodes;
    private String matchMode = "ANY";
}
