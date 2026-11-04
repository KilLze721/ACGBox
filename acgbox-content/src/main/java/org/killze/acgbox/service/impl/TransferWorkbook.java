package org.killze.acgbox.service.impl;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.util.ZipEntrySource;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.killze.acgbox.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.BiFunction;

/** 固定版本的作品文件协议；不执行公式，不访问图片和外部链接。 */
@Component
public class TransferWorkbook {
    public static final int FILE_LIMIT = 5 * 1024 * 1024;
    public static final int RESPONSE_LIMIT = 6 * 1024 * 1024;
    public static final int WORK_LIMIT = 500;
    public static final int RELATION_LIMIT = 10000;
    private static final long ENTRY_LIMIT = 20L * 1024 * 1024;
    private static final long ARCHIVE_LIMIT = 50L * 1024 * 1024;
    private static final Map<String, List<String>> SUMMARY_COLUMNS = Map.of(
            "tagSummary", List.of("tags", "tagName"),
            "companySummary", List.of("companies", "companyName"),
            "personSummary", List.of("persons", "personName"),
            "aliasSummary", List.of("aliases", "aliasName"),
            "linkSummary", List.of("external_links", "title"));
    private static final Set<String> DISPLAY_FIELDS = Set.of("adaptationTypeName", "regionName", "statusName", "broadcastTypeName", "bookTypeName", "seriesName");
    private static final Set<String> HIDDEN_FIELDS = Set.of("adaptationTypeId", "regionId", "status", "broadcastTypeId", "bookTypeId", "seriesId");
    private static final Set<String> NUMBERS = Set.of("sourceId", "adaptationTypeId", "regionId", "status",
            "seriesId", "personalRatingScore", "broadcastTypeId", "episodeCount", "bookTypeId",
            "volumeCount", "chapterCount", "sortOrder", "companyId", "personId", "tagId");
    /** 中文仅用于文件表头展示，内部字段、工作表名称和元数据键保持不变。 */
    private static final Map<String, String> HEADER_LABELS = Map.ofEntries(
            Map.entry("key", "配置项"), Map.entry("value", "配置值"),
            Map.entry("rowKey", "文件内关联编号"), Map.entry("sourceId", "来源作品ID"),
            Map.entry("name", "作品名称"), Map.entry("adaptationTypeId", "改编类型ID"),
            Map.entry("regionId", "地区ID"), Map.entry("coverImageUrl", "封面图片地址"),
            Map.entry("status", "状态"), Map.entry("description", "简介"),
            Map.entry("seriesId", "系列ID"), Map.entry("seriesSortOrder", "系列内排序"),
            Map.entry("personalRatingScore", "个人评分"), Map.entry("createdAt", "创建时间"),
            Map.entry("updatedAt", "更新时间"), Map.entry("broadcastTypeId", "放送类型ID"),
            Map.entry("episodeCount", "总集数"), Map.entry("airDate", "开播日期"),
            Map.entry("bookTypeId", "书籍类型ID"), Map.entry("publishDate", "首次发表日期"),
            Map.entry("volumeCount", "卷数"), Map.entry("chapterCount", "章节数"),
            Map.entry("aliasName", "别名"), Map.entry("sortOrder", "排序值"),
            Map.entry("companyId", "公司ID"), Map.entry("companyName", "公司名称"),
            Map.entry("role", "职责"), Map.entry("personId", "人物ID"),
            Map.entry("personName", "人物名称"), Map.entry("tagId", "标签ID"),
            Map.entry("tagName", "标签名称"), Map.entry("title", "链接标题"),
            Map.entry("url", "链接地址"), Map.entry("adaptationTypeName", "改编类型"),
            Map.entry("regionName", "地区"), Map.entry("statusName", "状态名称"),
            Map.entry("broadcastTypeName", "放送类型"), Map.entry("bookTypeName", "书籍类型"),
            Map.entry("seriesName", "系列名称"), Map.entry("tagSummary", "标签"),
            Map.entry("companySummary", "公司与职责"), Map.entry("personSummary", "人物与职责"),
            Map.entry("aliasSummary", "别名"), Map.entry("linkSummary", "外部链接"));
    private final JsonMapper json;

    static {
        ZipSecureFile.setMaxEntrySize(ENTRY_LIMIT);
    }

    public TransferWorkbook(JsonMapper json) {
        this.json = json;
    }

    public record Issue(String sheet, Long row, String field, String rowKey, String message) { }
    public record PendingCatalog(String table, String name) { }
    public record ImportRow(String rowKey, Long row, ObjectNode data, Map<String, Issue> locations,
                            Map<Long, PendingCatalog> pendingCatalogs) {
        public ImportRow {
            if (pendingCatalogs == null) pendingCatalogs = Map.of();
        }
        public ImportRow(String rowKey, Long row, ObjectNode data, Map<String, Issue> locations) {
            this(rowKey, row, data, locations, Map.of());
        }
    }
    public record Parsed(String fileKind, List<ImportRow> rows, long relationCount, Problems problems) { }

    /** 明细最多返回一百条，但统计始终记录所有已发现的问题。 */
    public static class Problems {
        public long errorCount;
        public long warningCount;
        public final List<Issue> errors = new ArrayList<>();
        public final List<Issue> warnings = new ArrayList<>();
        public void error(Issue issue) {
            errorCount++;
            if (errors.size() < 100) errors.add(issue);
        }
        public void warning(Issue issue) {
            warningCount++;
            if (warnings.size() < 100) warnings.add(issue);
        }
    }

    public LinkedHashMap<String, List<String>> headers(String type) {
        LinkedHashMap<String, List<String>> result = new LinkedHashMap<>();
        result.put("meta", List.of("key", "value"));
        List<String> works = new ArrayList<>(List.of("rowKey", "sourceId", "name", "adaptationTypeId", "regionId",
                "coverImageUrl", "status", "description", "seriesId", "seriesSortOrder", "personalRatingScore", "createdAt", "updatedAt"));
        works.addAll(type.equals("ANIME") ? List.of("broadcastTypeId", "episodeCount", "airDate")
                : List.of("bookTypeId", "publishDate", "volumeCount", "chapterCount"));
        works.addAll(List.of("adaptationTypeName", "regionName", "statusName", type.equals("ANIME") ? "broadcastTypeName" : "bookTypeName"));
        works.add("seriesName");
        result.put("works", works);
        result.put("aliases", List.of("rowKey", "aliasName", "sortOrder"));
        result.put("companies", List.of("rowKey", "companyId", "companyName", "role", "sortOrder"));
        result.put("persons", List.of("rowKey", "personId", "personName", "role", "sortOrder"));
        result.put("tags", List.of("rowKey", "tagId", "tagName"));
        result.put("external_links", List.of("rowKey", "title", "url", "sortOrder"));
        return result;
    }

    /** 完整生成且通过大小检查后返回，调用方此后才发送下载响应。 */
    public byte[] write(String type, String kind, Map<String, List<ObjectNode>> tables) {
        return write(type, kind, tables, false);
    }

    public byte[] writeNamed(String type, String kind, Map<String, List<ObjectNode>> tables) {
        return write(type, kind, tables, true);
    }

    private LinkedHashMap<String, List<String>> namedHeaders(String type) {
        var result = headers(type);
        var works = new ArrayList<>(List.of("rowKey", "name", "seriesName", "description",
                "tagSummary", "companySummary", "personSummary"));
        works.addAll(type.equals("ANIME") ? List.of("episodeCount", "broadcastTypeName", "adaptationTypeName", "airDate")
                : List.of("volumeCount", "chapterCount", "bookTypeName", "adaptationTypeName", "publishDate"));
        works.addAll(List.of("statusName", "regionName", "personalRatingScore", "coverImageUrl", "aliasSummary", "linkSummary"));
        result.put("works", works);
        result.put("companies", List.of("rowKey", "companyName", "role", "sortOrder"));
        result.put("persons", List.of("rowKey", "personName", "role", "sortOrder"));
        result.put("tags", List.of("rowKey", "tagName"));
        return result;
    }

    private byte[] write(String type, String kind, Map<String, List<ObjectNode>> tables, boolean named) {
        var summaries = named ? buildSummaries(tables) : Map.<String, Map<String, List<String>>>of();
        try (XSSFWorkbook workbook = new XSSFWorkbook(); LimitedOutput output = new LimitedOutput(FILE_LIMIT)) {
            CellStyle text = workbook.createCellStyle();
            text.setDataFormat(workbook.createDataFormat().getFormat("@"));
            text.setWrapText(true);
            for (var entry : (named ? namedHeaders(type) : headers(type)).entrySet()) {
                Sheet sheet = workbook.createSheet(entry.getKey());
                sheet.setDefaultColumnWidth(22);
                sheet.setDefaultRowHeightInPoints(named ? 16 : 24);
                Row head = sheet.createRow(0);
                head.setHeightInPoints(named ? 16 : 28);
                Drawing<?> drawing = sheet.createDrawingPatriarch();
                for (int i = 0; i < entry.getValue().size(); i++) {
                    String field = entry.getValue().get(i);
                    Cell heading = head.createCell(i, CellType.STRING);
                    heading.setCellValue(namedLabel(field, named));
                    ClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();
                    anchor.setCol1(i); anchor.setCol2(i + 3); anchor.setRow1(0); anchor.setRow2(5);
                    Comment comment = drawing.createCellComment(anchor);
                    comment.setString(workbook.getCreationHelper().createRichTextString(SUMMARY_COLUMNS.containsKey(field)
                            ? "仅供阅读，导入忽略此列；完整内容以对应的关联明细表为准"
                            : named && DISPLAY_FIELDS.contains(field)
                            ? "按名称精确匹配已有资料，状态请填写对应状态文字" : explanation(field, type)));
                    heading.setCellComment(comment);
                    sheet.setDefaultColumnStyle(i, text);
                    int width = switch (field) {
                        case "sourceId", "adaptationTypeId", "regionId", "seriesId", "broadcastTypeId",
                                "bookTypeId", "companyId", "personId", "tagId", "sortOrder",
                                "seriesSortOrder", "personalRatingScore" -> 16;
                        case "name", "aliasName", "companyName", "personName", "tagName", "title",
                                "adaptationTypeName", "regionName", "statusName", "broadcastTypeName", "bookTypeName", "seriesName" -> 32;
                        case "description", "url", "coverImageUrl" -> 60;
                        default -> 22;
                    };
                    if (named) width = switch (field) {
                        case "rowKey" -> 6;
                        case "name" -> 70;
                        case "tagSummary" -> 25;
                        case "seriesName", "companySummary", "personSummary", "coverImageUrl", "aliasSummary", "linkSummary" -> 20;
                        case "description" -> 30;
                        case "airDate", "publishDate" -> 15;
                        case "episodeCount", "volumeCount", "chapterCount", "broadcastTypeName", "bookTypeName",
                                "adaptationTypeName", "statusName", "regionName", "personalRatingScore" -> 10;
                        default -> width;
                    };
                    sheet.setColumnWidth(i, width * 256);
                    if (entry.getKey().equals("works") && HIDDEN_FIELDS.contains(field)) sheet.setColumnHidden(i, true);
                }
                sheet.createFreezePane(0, 1);
                List<ObjectNode> rows = new ArrayList<>();
                if (entry.getKey().equals("meta")) {
                    Map<String, String> meta = new LinkedHashMap<>();
                    meta.put("schemaVersion", named ? "2" : "1");
                    meta.put("targetType", type);
                    meta.put("fileKind", kind);
                    meta.put("exportedAt", OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString());
                    meta.put("notice", "仅批量新增；sourceId 不用于更新；资料 ID 仅对来源数据库有效；名称列仅展示，导入以隐藏 ID 和状态代码为准，需修改时请取消隐藏");
                    if (named) meta.put("notice", "仅批量新增；按编号关联各工作表，资料名称必须精确匹配当前数据库已有记录");
                    meta.forEach((key, value) -> rows.add(json.createObjectNode().put("key", key).put("value", value)));
                } else {
                    rows.addAll(tables.getOrDefault(entry.getKey(), List.of()));
                }
                for (int n = 0; n < rows.size(); n++) {
                    Row row = sheet.createRow(n + 1);
                    // 逐行保存固定高度，避免表格软件按换行内容自动撑高。
                    row.setHeightInPoints(named ? 16 : 24);
                    ObjectNode data = rows.get(n);
                    for (int i = 0; i < entry.getValue().size(); i++) {
                        String field = entry.getValue().get(i);
                        JsonNode value = data.get(field);
                        if (summaries.containsKey(field)) {
                            List<String> items = summaries.get(field).get(data.path("rowKey").asText());
                            if (items != null) {
                                boolean tags = field.equals("tagSummary");
                                int shown = tags ? items.size() : Math.min(3, items.size());
                                String summary = String.join(tags ? "/" : "、", items.subList(0, shown));
                                if (items.size() > shown) summary += "（另有 " + (items.size() - shown) + " 项）";
                                value = json.valueToTree(summary);
                            }
                        }
                        if (entry.getKey().equals("works") && field.equals("statusName") && data.hasNonNull("status")) {
                            value = json.valueToTree(statusName(type, data.path("status").asInt()));
                        }
                        if (value == null || value.isNull()) continue;
                        String string = value.asText();
                        if (string.length() > 32767) {
                            throw new BusinessException("作品 " + data.path("rowKey").asText("") + " 的 " + field + " 超过 Excel 单元格容量");
                        }
                        // 所有字段按字符串输出，原文以公式符号开头也不会变成公式。
                        row.createCell(i, CellType.STRING).setCellValue(string);
                    }
                }
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new BusinessException("Excel 文件生成失败或超过 5 MiB，请缩小导出范围");
        }
    }

    public Parsed read(String type, MultipartFile file) {
        return read(type, file, (table, name) -> List.of());
    }

    public Parsed read(String type, MultipartFile file, BiFunction<String, String, List<Long>> lookup) {
        if (file == null || file.isEmpty()) throw new BusinessException("请选择非空的 XLSX 文件");
        if (file.getSize() > FILE_LIMIT) throw new MaxUploadSizeExceededException(FILE_LIMIT);
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BusinessException("仅支持 .xlsx 文件");
        }
        Path temporary = null;
        long deadline = System.nanoTime() + 15_000_000_000L;
        try {
            temporary = Files.createTempFile("acgbox-import-", ".xlsx");
            try (InputStream input = file.getInputStream(); OutputStream output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                long size = 0;
                for (int n; (n = input.read(buffer)) != -1;) {
                    checkTime(deadline);
                    size += n;
                    if (size > FILE_LIMIT) throw new MaxUploadSizeExceededException(FILE_LIMIT);
                    output.write(buffer, 0, n);
                }
            }
            try (BoundedZip source = new BoundedZip(temporary, deadline)) {
                source.preflight();
                source.total = 0;
                try (OPCPackage pkg = OPCPackage.open(source); XSSFWorkbook book = new XSSFWorkbook(pkg)) {
                    if (!"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml".equals(book.getPackagePart().getContentType())) {
                        throw new BusinessException("仅支持标准 XLSX，不支持宏工作簿或其他格式");
                    }
                    return parse(book, type, deadline, lookup);
                }
            }
        } catch (BusinessException | MaxUploadSizeExceededException | org.springframework.dao.DataAccessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Excel 无法读取、包含不支持的内容或超过解压／处理限制，请检查文件");
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); }
                catch (IOException e) { temporary.toFile().deleteOnExit(); }
            }
        }
    }

    private Parsed parse(XSSFWorkbook book, String type, long deadline, BiFunction<String, String, List<Long>> lookup) {
        var expected = headers(type);
        var namedHeaders = namedHeaders(type);
        boolean named = false;
        Problems problems = new Problems();
        if (book.getNumberOfSheets() != expected.size()) throw new BusinessException("工作表数量不符合模板，请保留全部规定工作表");
        Map<String, List<ObjectNode>> tables = new LinkedHashMap<>();
        long relations = 0;
        long parsedBytes = 0;
        for (var table : expected.entrySet()) {
            var fields = named ? namedHeaders.get(table.getKey()) : table.getValue();
            Sheet sheet = book.getSheet(table.getKey());
            if (sheet == null) throw new BusinessException("缺少工作表：" + table.getKey());
            Row header = sheet.getRow(0);
            if (header == null || header.getLastCellNum() > fields.size()) {
                throw new BusinessException(table.getKey() + " 表头不符合模板");
            }
            List<String> columns = new ArrayList<>();
            // 先归一化再检查，防止中文、英文两列实际指向同一个字段。
            Map<String, String> accepted = new HashMap<>();
            for (String field : fields) {
                accepted.put(field, field);
                accepted.put(namedLabel(field, named), field);
            }
            for (int c = 0; c < header.getLastCellNum(); c++) {
                Cell cell = header.getCell(c);
                if (cell == null || cell.getCellType() != CellType.STRING) throw new BusinessException("表头必须为规定的中文标题或原英文字段名");
                String field = accepted.get(cell.getStringCellValue());
                if (field == null) throw new BusinessException(table.getKey() + " 包含未知表头，请使用模板规定的中文标题或原英文字段名");
                columns.add(field);
            }
            Set<String> required = new HashSet<>(fields);
            if (named) {
                required.removeAll(SUMMARY_COLUMNS.keySet());
                required.remove("coverImageUrl");
            } else {
                required.removeAll(DISPLAY_FIELDS);
                required.remove("seriesSortOrder");
            }
            if (new HashSet<>(columns).size() != columns.size() || !columns.containsAll(required)) {
                throw new BusinessException(table.getKey() + " 包含重复、未知或缺失表头");
            }
            if (!named && table.getKey().equals("works") && columns.stream().anyMatch(DISPLAY_FIELDS::contains)) {
                problems.warning(new Issue("works", 1L, "", "", "名称和状态文字列仅供展示，导入以 ID 和状态代码为准，修改文字不会改变关联"));
            }
            List<ObjectNode> rows = new ArrayList<>();
            int physical = 0;
            for (Row row : sheet) {
                checkTime(deadline);
                if (row.getRowNum() == 0) continue;
                if (++physical > RELATION_LIMIT + WORK_LIMIT + 100) throw new BusinessException("文件行数超过允许范围");
                boolean nonempty = false;
                for (Cell cell : row) {
                    if (cell.getCellType() != CellType.BLANK && !(cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank())) nonempty = true;
                    if (cell.getColumnIndex() >= columns.size() && cell.getCellType() != CellType.BLANK) throw new BusinessException(table.getKey() + " 存在表头外的数据列");
                }
                if (!nonempty) continue;
                ObjectNode data = json.createObjectNode();
                data.put("_row", row.getRowNum() + 1);
                for (int c = 0; c < columns.size(); c++) {
                    String field = columns.get(c);
                    try {
                        readCell(data, field, row.getCell(c), table.getKey().equals("meta"), type);
                    } catch (BusinessException e) {
                        problems.error(new Issue(table.getKey(), (long) row.getRowNum() + 1, field, data.path("rowKey").asText(""), e.getMessage()));
                    }
                }
                parsedBytes += json.writeValueAsBytes(data).length;
                if (parsedBytes > FILE_LIMIT) throw new BusinessException("文件文本和标准化数据超过 5 MiB，请缩小范围");
                if (table.getKey().equals("works") && data.hasNonNull("status")) {
                    long status = data.path("status").asLong();
                    if (status < (type.equals("ANIME") ? 1 : 0) || status > (type.equals("ANIME") ? 4 : 5)) {
                        problems.error(new Issue("works", (long) row.getRowNum() + 1, "status", data.path("rowKey").asText(""), "状态不在当前作品类型允许范围内"));
                    }
                }
                rows.add(data);
                if (table.getKey().equals("works") && rows.size() > WORK_LIMIT) throw new BusinessException("单次最多导入 500 部作品");
                if (!table.getKey().equals("works") && !table.getKey().equals("meta") && ++relations > RELATION_LIMIT) throw new BusinessException("关联总数不能超过 10000 条");
            }
            tables.put(table.getKey(), rows);
            if (table.getKey().equals("meta")) named = rows.stream().anyMatch(row ->
                    "schemaVersion".equals(row.path("key").asText()) && "2".equals(row.path("value").asText()));
        }
        Map<String, String> meta = new HashMap<>();
        for (var row : tables.get("meta")) {
            String key = row.path("key").asText("");
            if (meta.put(key, row.path("value").asText("")) != null) throw new BusinessException("meta 包含重复键");
        }
        if (!meta.containsKey("exportedAt") || meta.get("exportedAt").isBlank()
                || !(named ? "2" : "1").equals(meta.get("schemaVersion")) || !type.equals(meta.get("targetType"))
                || !Set.of("EXPORT", "TEMPLATE").contains(meta.getOrDefault("fileKind", ""))) {
            throw new BusinessException("文件版本、类型或 fileKind 与接口不一致");
        }
        if (named && problems.errorCount == 0) resolveNames(type, tables, problems, lookup);
        LinkedHashMap<String, ImportRow> works = new LinkedHashMap<>();
        for (ObjectNode work : tables.get("works")) {
            String key = work.path("rowKey").asText("");
            long row = work.path("_row").asLong();
            if (key.isBlank() || key.length() > 100 || works.containsKey(key)) {
                problems.error(new Issue("works", row, "rowKey", key, "rowKey 不能为空、超过 100 字符或重复"));
                continue;
            }
            ObjectNode data = work.deepCopy();
            data.remove(List.of("_row", "rowKey", "sourceId", "createdAt", "updatedAt", "seriesSortOrder"));
            data.remove(DISPLAY_FIELDS);
            data.remove(SUMMARY_COLUMNS.keySet());
            data.put("autoCreateSeries", false);
            data.putArray("aliasNames"); data.putArray("companies"); data.putArray("persons"); data.putArray("tagIds"); data.putArray("externalLinks");
            Map<String, Issue> locations = new LinkedHashMap<>();
            for (String field : columnsForWork(type)) locations.put(field, new Issue("works", row, field, key, ""));
            works.put(key, new ImportRow(key, row, data, locations));
        }
        for (String sheet : List.of("aliases", "companies", "persons", "tags", "external_links")) {
            List<ObjectNode> entries = tables.get(sheet);
            if (sheet.equals("aliases")) entries.sort(Comparator.comparing((ObjectNode a) -> a.path("sortOrder").asLong(0)).thenComparing(a -> a.path("_row").asLong()));
            for (ObjectNode entry : entries) {
                String key = entry.path("rowKey").asText("");
                ImportRow work = works.get(key);
                if (work == null) {
                    problems.error(new Issue(sheet, entry.path("_row").asLong(), "rowKey", key, "关联指向不存在的 rowKey"));
                    continue;
                }
                String property = switch (sheet) { case "aliases" -> "aliasNames"; case "tags" -> "tagIds"; case "external_links" -> "externalLinks"; default -> sheet; };
                var array = work.data().withArray(property);
                int index = array.size();
                for (String field : headers(type).get(sheet)) {
                    work.locations().put(property + "[" + index + "]." + field, new Issue(sheet, entry.path("_row").asLong(), field, key, ""));
                }
                if (sheet.equals("aliases")) {
                    if (entry.path("aliasName").asText("").isBlank()) problems.error(new Issue(sheet, entry.path("_row").asLong(), "aliasName", key, "别名行不完整，请填写 aliasName 或清空整行"));
                    array.add(entry.path("aliasName").asText(""));
                }
                else if (sheet.equals("tags")) array.add(entry.get("tagId") == null ? json.nullNode() : entry.get("tagId"));
                else {
                    ObjectNode relation = entry.deepCopy();
                    relation.remove(List.of("_row", "rowKey", "companyName", "personName", "tagName"));
                    array.add(relation);
                }
                // 辅助名称保留在定位信息中，不参与 ID 绑定或创建资料。
                for (String name : List.of("companyName", "personName", "tagName")) {
                    if (entry.hasNonNull(name)) work.locations().put(property + "[" + index + "]._displayName",
                            new Issue(sheet, entry.path("_row").asLong(), name, key, entry.get(name).asText()));
                }
            }
        }
        if (works.isEmpty()) problems.error(new Issue("works", 2L, "rowKey", "", "文件没有可新增的作品"));
        return new Parsed(meta.get("fileKind"), new ArrayList<>(works.values()), relations, problems);
    }

    private Map<String, Map<String, List<String>>> buildSummaries(Map<String, List<ObjectNode>> tables) {
        Map<String, Map<String, List<String>>> result = new HashMap<>();
        SUMMARY_COLUMNS.forEach((field, source) -> {
            Map<String, List<String>> byWork = new HashMap<>();
            for (ObjectNode row : tables.getOrDefault(source.getFirst(), List.of())) {
                String label = row.path(source.get(1)).asText("");
                if (field.equals("linkSummary")) {
                    String url = row.path("url").asText("");
                    if (!url.isBlank()) label = label.isBlank() ? url : label + "（" + url + "）";
                }
                String role = row.path("role").asText("");
                if (!role.isBlank()) label += "（" + role + "）";
                if (!label.isBlank()) byWork.computeIfAbsent(row.path("rowKey").asText(), key -> new ArrayList<>()).add(label);
            }
            result.put(field, byWork);
        });
        return result;
    }

    private List<String> columnsForWork(String type) { return headers(type).get("works"); }

    private String namedLabel(String field, boolean named) {
        if (named && field.equals("coverImageUrl")) return "封面 URL";
        if (named && field.equals("rowKey")) return "编号";
        if (named && field.equals("publishDate")) return "发表日期";
        return HEADER_LABELS.get(field);
    }

    private void resolveNames(String type, Map<String, List<ObjectNode>> tables, Problems problems,
                              BiFunction<String, String, List<Long>> lookup) {
        boolean anime = type.equals("ANIME");
        for (ObjectNode row : tables.get("works")) {
            resolveName("works", row, "adaptationTypeName", "adaptationTypeId", "adaptation_type", true, problems, lookup);
            resolveName("works", row, "regionName", "regionId", "region", true, problems, lookup);
            resolveName("works", row, "seriesName", "seriesId", "series", false, problems, lookup);
            resolveName("works", row, anime ? "broadcastTypeName" : "bookTypeName",
                    anime ? "broadcastTypeId" : "bookTypeId", anime ? "broadcast_type" : "book_type", true, problems, lookup);
            String status = row.path("statusName").asText("");
            for (int code = anime ? 1 : 0; code <= (anime ? 4 : 5); code++) {
                if (statusName(type, code).equals(status)) row.put("status", code);
            }
            if (!row.has("status")) problems.error(new Issue("works", row.path("_row").asLong(), "statusName",
                    row.path("rowKey").asText(""), "请填写有效的状态名称"));
        }
        for (ObjectNode row : tables.get("companies")) resolveName("companies", row, "companyName", "companyId", "company", true, problems, lookup);
        for (ObjectNode row : tables.get("persons")) resolveName("persons", row, "personName", "personId", "person", true, problems, lookup);
        for (ObjectNode row : tables.get("tags")) resolveName("tags", row, "tagName", "tagId", "tag", true, problems, lookup);
    }

    private void resolveName(String sheet, ObjectNode row, String field, String idField, String table, boolean required,
                             Problems problems, BiFunction<String, String, List<Long>> lookup) {
        String name = row.path(field).asText("");
        if (name.isBlank() && !required) return;
        List<Long> ids;
        try {
            ids = name.isBlank() ? List.of() : lookup.apply(table, name);
        } catch (BusinessException e) {
            problems.error(new Issue(sheet, row.path("_row").asLong(), field, row.path("rowKey").asText(""), e.getMessage()));
            return;
        }
        if (ids.size() == 1) {
            row.put(idField, ids.getFirst());
        } else {
            problems.error(new Issue(sheet, row.path("_row").asLong(), field, row.path("rowKey").asText(""),
                    name.isBlank() ? "名称不能为空" : ids.isEmpty() ? "未找到完全同名的资料" : "存在多条同名资料，无法确定关联"));
        }
    }

    private String statusName(String type, int status) {
        if (type.equals("ANIME")) return switch (status) {
            case 1 -> "未放送"; case 2 -> "放送中"; case 3 -> "已完结"; case 4 -> "其他";
            default -> "未知状态（" + status + "）";
        };
        return switch (status) {
            case 0 -> "其他"; case 1 -> "未发表"; case 2 -> "连载中"; case 3 -> "已完结";
            case 4 -> "暂停中"; case 5 -> "已终止"; default -> "未知状态（" + status + "）";
        };
    }

    private String explanation(String field, String type) {
        if (DISPLAY_FIELDS.contains(field)) return "仅供展示，修改此列不会改变关联或状态；导入以隐藏的 ID／状态代码为准，请取消隐藏后修改对应列";
        if (field.equals("seriesId")) return "此列默认隐藏；可选的已有系列 ID，空白不加入系列，不自动创建系列";
        if (HIDDEN_FIELDS.contains(field)) return (field.equals("status")
                ? (type.equals("ANIME") ? "必填：1 未放送、2 放送中、3 已完结、4 其他" : "必填：0 其他、1 未发表、2 连载中、3 已完结、4 暂停中、5 已终止")
                : "必须引用当前数据库已有资料 ID") + "；此列默认隐藏，导入以此列为准，可取消隐藏后修改";
        return switch (field) {
            case "rowKey" -> "文件内唯一字符串，必填，最多 100 字符；关联表按此字段定位作品，不是数据库 ID";
            case "sourceId", "createdAt", "updatedAt" -> "只读追溯信息，导入时忽略，不指定新 ID，也不更新原作品";
            case "airDate", "publishDate" -> "真实完整日期 yyyy-MM-dd 文本或空白，请勿改为 Excel 日期数值";
            case "personalRatingScore" -> "0 到 10 的数值整数，8.0 等价于 8，空白为未评分，0 是有效评分";
            case "status" -> type.equals("ANIME") ? "必填：1 未放送、2 放送中、3 已完结、4 其他" : "必填：0 其他、1 未发表、2 连载中、3 已完结、4 暂停中、5 已终止";
            case "name" -> "作品名称必填；动画按原名称判重，书籍允许同名";
            case "companyName", "personName", "tagName" -> "可选辅助名称，只作核对，不参与 ID 匹配，不会创建资料";
            case "url", "coverImageUrl" -> "地址文本，不下载或探测远程资源，不嵌入图片";
            case "description" -> "可空，保留原文换行；最多 32767 个 Excel 文本字符，不截断";
            case "role" -> "人物职责必填，同人不同职责允许；公司职责可空，长度沿用现有规则";
            default -> NUMBERS.contains(field) ? "十进制整数，空白及范围沿用现有新增规则；资料字段必须引用当前数据库已有 ID" : "普通文本，保留固定表头；字段要求见管理端接口文档";
        };
    }

    private void readCell(ObjectNode data, String field, Cell cell, boolean meta, String type) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return;
        if (cell.getCellType() == CellType.FORMULA) throw new BusinessException("不允许公式单元格，请改为普通值");
        if (cell.getCellType() != CellType.STRING && cell.getCellType() != CellType.NUMERIC) throw new BusinessException("只允许文本或数值单元格");
        boolean readOnly = meta || Set.of("sourceId", "createdAt", "updatedAt").contains(field);
        if (!readOnly && cell.getCellType() == CellType.NUMERIC && (field.equals("airDate") || field.equals("publishDate") || DateUtil.isCellDateFormatted(cell))) {
            throw new BusinessException("日期必须使用 yyyy-MM-dd 文本，不接受 Excel 日期数值");
        }
        String value = cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : NumberToTextConverter.toText(cell.getNumericCellValue());
        if (value.length() > 32767) throw new BusinessException("文本超过单元格容量");
        if (value.isBlank()) return;
        int max = switch (field) {
            case "name", "aliasName" -> 200;
            case "role" -> 50;
            case "title" -> 100;
            case "url", "coverImageUrl" -> 500;
            default -> 32767;
        };
        String checked = Set.of("aliasName", "role", "title", "url").contains(field)
                || (type.equals("BOOK") && Set.of("name", "coverImageUrl").contains(field)) ? value.trim() : value;
        if (!meta && checked.codePointCount(0, checked.length()) > max) throw new BusinessException("字段不能超过 " + max + " 字符");
        if (meta || field.equals("sourceId") || !NUMBERS.contains(field)) {
            if (field.equals("airDate") || field.equals("publishDate")) {
                try {
                    if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || LocalDate.parse(value).getYear() < 1) throw new IllegalArgumentException();
                } catch (Exception e) { throw new BusinessException("日期必须为有效的 yyyy-MM-dd 文本"); }
            }
            data.put(field, value);
            return;
        }
        if (!value.matches("-?[0-9]+(?:\\.[0-9]+)?") || (cell.getCellType() == CellType.NUMERIC && cell.getCellStyle().getDataFormatString().contains("%"))) {
            throw new BusinessException("必须为规范十进制整数，不支持千分位、百分数或科学计数文本");
        }
        try {
            BigDecimal number = new BigDecimal(value);
            if (field.equals("personalRatingScore")) data.put(field, number);
            else {
                long integer = number.longValueExact();
                if (integer < -2147483648L || integer > 2147483647L) throw new ArithmeticException();
                data.put(field, integer);
            }
        } catch (ArithmeticException e) { throw new BusinessException("必须为范围内的整数，不能截断小数"); }
    }

    public static void checkTime(long deadline) {
        if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline) throw new BusinessException("Excel 处理超过 15 秒，请缩小文件后重试");
    }

    /** 实际读取累计上限，覆盖归档预检及后续 POI 解析；不信任 ZIP 声明长度。 */
    private static class BoundedZip implements ZipEntrySource {
        private final ZipSecureFile file;
        private final long deadline;
        private long total;
        private long xmlElements;
        private long xmlText;
        private long cells;
        private long strings;
        private boolean closed;
        BoundedZip(Path path, long deadline) throws IOException { this.file = new ZipSecureFile(path.toFile()); this.deadline = deadline; }
        public Enumeration<? extends ZipArchiveEntry> getEntries() { checkTime(deadline); return file.getEntries(); }
        public ZipArchiveEntry getEntry(String path) { checkTime(deadline); return file.getEntry(path); }
        public boolean isClosed() { return closed; }
        public void close() throws IOException { if (!closed) { closed = true; file.close(); } }
        public InputStream getInputStream(ZipArchiveEntry entry) throws IOException {
            checkTime(deadline);
            return new FilterInputStream(file.getInputStream(entry)) {
                private long size;
                public int read() throws IOException {
                    byte[] b = new byte[1]; return read(b, 0, 1) == -1 ? -1 : b[0] & 255;
                }
                public int read(byte[] b, int off, int len) throws IOException {
                    checkTime(deadline);
                    int n = in.read(b, off, Math.min(len, 8192));
                    if (n > 0) { size += n; total += n; }
                    if (size > ENTRY_LIMIT || total > ARCHIVE_LIMIT) throw new IOException("解压数据超过限制");
                    return n;
                }
                public long skip(long count) throws IOException {
                    byte[] buffer = new byte[8192];
                    long skipped = 0;
                    while (skipped < count) {
                        int n = read(buffer, 0, (int) Math.min(buffer.length, count - skipped));
                        if (n == -1) break;
                        skipped += n;
                    }
                    return skipped;
                }
            };
        }
        void preflight() throws Exception {
            int count = 0;
            var entries = getEntries();
            while (entries.hasMoreElements()) {
                checkTime(deadline);
                ZipArchiveEntry entry = entries.nextElement();
                if (++count > 1000) throw new BusinessException("归档条目过多");
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (name.contains("vbaproject") || name.contains("/embeddings/") || name.contains("/externallinks/") || name.contains("/media/")) throw new BusinessException("不支持宏、嵌入对象、图片或外部工作簿链接");
                try (InputStream input = getInputStream(entry)) {
                    if (name.endsWith(".rels") || name.endsWith(".xml") || name.endsWith(".vml")) {
                        XMLInputFactory factory = XMLInputFactory.newFactory();
                        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
                        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
                        factory.setProperty(XMLInputFactory.IS_COALESCING, false);
                        var reader = factory.createXMLStreamReader(input);
                        long rows = 0;
                        try {
                            while (reader.hasNext()) {
                                checkTime(deadline);
                                int event = reader.next();
                                if (event == XMLStreamConstants.DTD || event == XMLStreamConstants.ENTITY_REFERENCE) throw new BusinessException("不支持 DTD 或实体引用");
                                if (event == XMLStreamConstants.START_ELEMENT) {
                                    if (++xmlElements > 500000) throw new BusinessException("文件 XML 结构过于复杂，请缩小范围");
                                    String local = reader.getLocalName();
                                    if (name.startsWith("xl/worksheets/") && local.equals("row") && ++rows > RELATION_LIMIT + WORK_LIMIT + 100) throw new BusinessException("文件行数超过允许范围");
                                    if (name.startsWith("xl/worksheets/") && local.equals("c") && ++cells > 80000) throw new BusinessException("文件单元格数量过多");
                                    if (name.equals("xl/sharedstrings.xml") && local.equals("si") && ++strings > 80000) throw new BusinessException("文件共享文本数量过多");
                                    if ("External".equalsIgnoreCase(reader.getAttributeValue(null, "TargetMode"))) throw new BusinessException("文件不能包含外部链接关系");
                                    String relation = reader.getAttributeValue(null, "Type");
                                    if (relation != null && (relation.endsWith("/oleObject") || relation.endsWith("/package") || relation.endsWith("/image"))) throw new BusinessException("不支持嵌入对象或图片");
                                    String content = reader.getAttributeValue(null, "ContentType");
                                    if (content != null && content.toLowerCase(Locale.ROOT).contains("macroenabled")) throw new BusinessException("不支持宏工作簿");
                                } else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                                    if (!reader.isWhiteSpace()) xmlText += reader.getText().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
                                    if (xmlText > RESPONSE_LIMIT) throw new BusinessException("文件文本数据过多，请缩小范围");
                                }
                            }
                        } finally { reader.close(); }
                    } else {
                        byte[] buffer = new byte[8192];
                        while (input.read(buffer) != -1) checkTime(deadline);
                    }
                }
            }
        }
    }

    public static class LimitedOutput extends ByteArrayOutputStream {
        private final int limit;
        public LimitedOutput(int limit) { this.limit = limit; }
        public synchronized void write(int b) { check(1); super.write(b); }
        public synchronized void write(byte[] b, int off, int len) { check(len); super.write(b, off, len); }
        private void check(int extra) { if ((long) count + extra > limit) throw new BusinessException("文件或数据超过大小限制，请缩小范围"); }
    }
}
