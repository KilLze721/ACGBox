package org.killze.acgbox.service;

import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** 动画、书籍分组导入导出，调用方传入已认证账号，不公开数据库和缓存细节。 */
public interface DataTransferService {
    /** 已完整生成且通过大小检查的下载文件。 */
    record Download(String filename, byte[] bytes) { }
    Download template(String type, String requested);
    Download export(String type, JsonNode request);
    ObjectNode preview(String type, String requested, long owner, MultipartFile file);
    ObjectNode commit(String type, JsonNode request, long owner);
    ObjectNode batch(String type, String requested, String token, long owner, boolean cancel);
}
