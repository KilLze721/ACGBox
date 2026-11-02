package org.killze.acgbox.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.result.Result;
import org.killze.acgbox.service.DataTransferService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.security.Principal;

/** 动画与书籍使用固定分组入口，权限复用现有 ADMIN、EDITOR 规则。 */
@RestController
@RequestMapping("/data-transfer")
public class DataTransferController {
    private final DataTransferService service;
    public DataTransferController(DataTransferService service) { this.service = service; }

    @PostMapping({"/anime/export", "/book/export"})
    public ResponseEntity<byte[]> export(@RequestBody JsonNode body, HttpServletRequest request) {
        return file(service.export(type(request), body));
    }
    @GetMapping({"/anime/template", "/book/template"})
    public ResponseEntity<byte[]> template(@RequestParam String targetType, HttpServletRequest request) {
        return file(service.template(type(request), targetType));
    }
    @PostMapping(value = {"/anime/import/preview", "/book/import/preview"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ObjectNode> preview(MultipartHttpServletRequest request, Principal principal) {
        if (request.getMultiFileMap().values().stream().mapToInt(java.util.List::size).sum() != 1 || request.getFiles("file").size() != 1) throw new BusinessException("请通过 file 上传且仅上传一个 XLSX 文件");
        String[] values = request.getParameterValues("targetType");
        if (values == null || values.length != 1) throw new BusinessException("请提交唯一的 targetType");
        return Result.success(service.preview(type(request), values[0], owner(principal), request.getFile("file")));
    }
    @PostMapping({"/anime/import/commit", "/book/import/commit"})
    public Result<ObjectNode> commit(@RequestBody JsonNode body, HttpServletRequest request, Principal principal) {
        ObjectNode result = service.commit(type(request), body, owner(principal));
        return result(result);
    }
    @GetMapping({"/anime/import/{importToken}", "/book/import/{importToken}"})
    public Result<ObjectNode> batch(@PathVariable String importToken, @RequestParam String targetType, HttpServletRequest request, Principal principal) {
        return result(service.batch(type(request), targetType, importToken, owner(principal), false));
    }
    @PostMapping({"/anime/import/{importToken}/cancel", "/book/import/{importToken}/cancel"})
    public Result<ObjectNode> cancel(@PathVariable String importToken, @RequestBody JsonNode body, HttpServletRequest request, Principal principal) {
        return result(service.batch(type(request), body.path("targetType").asText(), importToken, owner(principal), true));
    }
    private Result<ObjectNode> result(ObjectNode data) {
        String state = data.path("status").asText();
        return Result.<ObjectNode>builder().code(state.equals("INVALID") || state.equals("EXPIRED") ? 500 : 200)
                .message(data.path("message").asText("操作成功")).data(data).build();
    }

    /** 数据库故障不暴露 SQL、连接信息或令牌，也不能将不确定结果说成未导入。 */
    @ExceptionHandler({org.springframework.dao.DataAccessException.class, org.springframework.transaction.TransactionException.class})
    public Result<Void> storageFailure(Exception exception) {
        return Result.error("导入导出服务暂不可用，请检查批次表和数据库；已提交请求请查询或使用同一 token 重试，勿重新导入");
    }
    private ResponseEntity<byte[]> file(DataTransferService.Download download) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(download.bytes().length).header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(download.filename()).build().toString()).body(download.bytes());
    }
    private long owner(Principal principal) { return Long.parseLong(principal.getName()); }
    private String type(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path.startsWith("/data-transfer/anime/")) return "ANIME";
        if (path.startsWith("/data-transfer/book/")) return "BOOK";
        throw new BusinessException("不支持的数据传输入口");
    }
}
