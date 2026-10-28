package org.killze.acgbox.service;

import org.killze.acgbox.vo.content.ImageUploadVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 动画与书籍共用的封面图片上传服务。
 */
public interface ImageUploadService {
    /** 校验单张图片并上传，不创建或修改作品。 */
    ImageUploadVO uploadImage(MultipartFile file, String targetType);
}
