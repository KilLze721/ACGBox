package org.killze.acgbox.vo.content;

import lombok.Builder;
import lombok.Data;

/**
 * 图片上传结果，不表示作品已经保存或已与图片建立关联。
 */
@Data
@Builder
public class ImageUploadVO {
    /** 固定 HTTPS 公开访问地址。 */
    private String url;
    /** 桶内对象键，不包含凭证和本地路径。 */
    private String objectKey;
    /** 业务类型：ANIME 或 BOOK。 */
    private String targetType;
    /** 后端检测得到的实际图片类型。 */
    private String contentType;
    /** 实际上传的原始字节数。 */
    private Long size;
    /** 图片宽度，单位像素。 */
    private Long width;
    /** 图片高度，单位像素。 */
    private Long height;
}
