package org.killze.acgbox.controller;

import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.result.Result;
import org.killze.acgbox.service.ImageUploadService;
import org.killze.acgbox.vo.content.ImageUploadVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/**
 * 图片上传接口，沿用现有 ADMIN、EDITOR 内容管理权限。
 */
@RestController
@RequestMapping("/uploads")
public class ImageUploadController {
    @Autowired
    private ImageUploadService imageUploadService;

    /**
     * 只接收一个 file 和一个 targetType，避免批量文件被静默忽略。
     */
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ImageUploadVO> uploadImage(MultipartHttpServletRequest request) {
        var files = request.getMultiFileMap();
        if (files.values().stream().mapToInt(java.util.List::size).sum() != 1
                || request.getFiles("file").size() != 1) {
            throw new BusinessException("请通过 file 上传且仅上传一张图片");
        }
        String[] targetTypes = request.getParameterValues("targetType");
        if (targetTypes == null || targetTypes.length != 1) {
            throw new BusinessException("targetType 必须为 ANIME 或 BOOK");
        }
        MultipartFile file = request.getFile("file");
        return Result.success(imageUploadService.uploadImage(file, targetTypes[0]));
    }
}
