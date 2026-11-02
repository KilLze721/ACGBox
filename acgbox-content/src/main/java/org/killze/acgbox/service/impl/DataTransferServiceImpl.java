package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Validator;
import org.killze.acgbox.dto.content.*;
import org.killze.acgbox.entity.content.*;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.mapper.*;
import org.killze.acgbox.result.Result;
import org.killze.acgbox.service.DataTransferService;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;
import static org.killze.acgbox.service.impl.TransferWorkbook.*;

/** 作品文件传输协调：Redis 只存预览，数据库批次是执行状态的唯一权威来源。 */
@Service
@Slf4j
public class DataTransferServiceImpl implements DataTransferService {
    private static final String CACHE_PREFIX = "acgbox:data-transfer:preview:";
    @Autowired private JsonMapper json;
    @Autowired private TransferWorkbook workbook;
    @Autowired private AnimeServiceImpl animeService;
    @Autowired private BookServiceImpl bookService;
    @Autowired private AnimeMapper animeMapper;
    @Autowired private BookMapper bookMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StringRedisTemplate redis;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private SqlSessionTemplate sessions;
    @Autowired private Validator validator;

    private record Batch(String token, long owner, String type, String state, String payloadHash,
                         Instant expiresAt, String result, String reason, long workCount) { }

    public void requireType(String type, String requested) {
        if (!type.equals(requested)) throw new BusinessException("targetType 必须与当前接口类型 " + type + " 一致");
    }

    public Download template(String type, String requested) {
        requireType(type, requested);
        return download(type, workbook.write(type, "TEMPLATE", Map.of()));
    }

    public Download export(String type, JsonNode request) {
        requireType(type, request.path("targetType").asText());
        var tables = readTransaction(() -> exportSnapshot(type, request));
        return download(type, workbook.write(type, "EXPORT", tables));
    }

    private Download download(String type, byte[] bytes) {
        String stamp = LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return new Download("ACGBox_" + type + "_" + stamp + ".xlsx", bytes);
    }

    private Map<String, List<ObjectNode>> exportSnapshot(String type, JsonNode request) {
        List<Long> ids;
        String scope = request.path("scope").asText();
        String table = type.equals("ANIME") ? "anime" : "book";
        if (scope.equals("SELECTED")) {
            if (request.hasNonNull("filters")) throw new BusinessException("已勾选导出不能同时提交 filters");
            JsonNode input = request.path("ids");
            if (!input.isArray() || input.isEmpty()) throw new BusinessException("请选择 1 到 500 部作品");
            TreeSet<Long> selected = new TreeSet<>();
            for (JsonNode id : input) {
                if (!id.isIntegralNumber() || !id.canConvertToLong() || id.asLong() < 1 || id.asLong() > 2147483647L) throw new BusinessException("作品 ID 必须为有效整数");
                selected.add(id.asLong());
                if (selected.size() > 500) throw new BusinessException("单次最多导出 500 部作品");
            }
            ids = new ArrayList<>(selected);
            String placeholders = placeholders(ids.size());
            Long count = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE id IN (" + placeholders + ")", Long.class, ids.toArray());
            if (count == null || count != ids.size()) throw new BusinessException("所选作品不存在，请刷新后重试");
        } else if (scope.equals("FILTERED")) {
            if (request.hasNonNull("ids")) throw new BusinessException("筛选导出不能同时提交 ids");
            JsonNode filters = request.get("filters");
            if (filters == null || !filters.isObject()) throw new BusinessException("filters 必须为已应用的筛选对象，无筛选时传空对象");
            Class<?> filterType = type.equals("ANIME") ? AnimePageDTO.class : BookPageDTO.class;
            Set<String> accepted = new HashSet<>();
            for (var property : org.springframework.beans.BeanUtils.getPropertyDescriptors(filterType)) {
                if (property.getWriteMethod() != null) accepted.add(property.getName());
            }
            for (String field : filters.propertyNames()) {
                if (!accepted.contains(field)) throw new BusinessException("当前作品类型不支持该筛选字段");
            }
            ObjectNode copy = (ObjectNode) filters.deepCopy();
            copy.remove(List.of("page", "pageNum", "pageSize"));
            if (type.equals("ANIME")) ids = animeService.selectAnimeForExport(json.treeToValue(copy, AnimePageDTO.class)).stream().map(Anime::getId).toList();
            else ids = bookService.selectBookForExport(json.treeToValue(copy, BookPageDTO.class)).stream().map(Book::getId).toList();
        } else throw new BusinessException("scope 仅支持 SELECTED 或 FILTERED");
        Map<String, List<ObjectNode>> tables = new LinkedHashMap<>();
        workbook.headers(type).keySet().forEach(key -> tables.put(key, new ArrayList<>()));
        if (ids.isEmpty()) return tables;
        checkSnapshotBounds(type, table, ids);
        long bytes = 0;
        for (int n = 0; n < ids.size(); n++) {
            Long id = ids.get(n);
            String key = String.format(Locale.ROOT, "W%03d", n + 1);
            ObjectNode data;
            try {
                data = (ObjectNode) json.valueToTree(type.equals("ANIME") ? animeService.getAnimeById(id) : bookService.getBookById(id));
            } catch (BusinessException e) { throw new BusinessException("作品 " + id + " 无法导出：" + e.getMessage()); }
            JsonNode score = data.get("personalRatingScore");
            if (score != null && !score.isNull()) {
                var value = score.decimalValue();
                if (value.stripTrailingZeros().scale() > 0 || value.signum() < 0 || value.compareTo(java.math.BigDecimal.TEN) > 0) throw new BusinessException("作品 " + id + " 的个人评分不是 0 到 10 整数，请先核对历史数据");
            }
            ObjectNode basic = json.createObjectNode().put("rowKey", key).put("sourceId", id);
            for (String field : workbook.headers(type).get("works")) {
                if (data.has(field)) basic.set(field, data.get(field));
            }
            Map<String, Object> times = jdbc.queryForMap("SELECT created_at, updated_at FROM " + table + " WHERE id=?", id);
            times.forEach((field, value) -> { if (value != null) basic.put(field.equals("created_at") ? "createdAt" : "updatedAt", value.toString()); });
            tables.get("works").add(basic);
            for (String property : List.of("aliasNames", "companies", "persons", "tagIds", "externalLinks")) {
                String sheet = switch (property) { case "aliasNames" -> "aliases"; case "tagIds" -> "tags"; case "externalLinks" -> "external_links"; default -> property; };
                int index = 0;
                for (JsonNode value : data.path(property)) {
                    ObjectNode row;
                    if (property.equals("aliasNames")) row = json.createObjectNode().put("aliasName", value.asText()).put("sortOrder", index);
                    else if (property.equals("tagIds")) row = json.createObjectNode().put("tagId", value.asLong());
                    else row = ((ObjectNode) value).deepCopy();
                    row.put("rowKey", key);
                    if (property.equals("companies")) row.put("companyName", referenceName("company", row.path("companyId").asLong()));
                    if (property.equals("tagIds")) row.put("tagName", referenceName("tag", value.asLong()));
                    tables.get(sheet).add(row);
                    index++;
                }
            }
            // 每部作品提取完成即累计，不能先构造无限快照再判断。
            bytes += json.writeValueAsBytes(data).length;
            if (bytes > FILE_LIMIT) throw new BusinessException("导出标准化数据超过 5 MiB，请缩小范围");
        }
        limitedJson(tables, FILE_LIMIT);
        return tables;
    }

    private void checkSnapshotBounds(String type, String table, List<Long> ids) {
        String in = placeholders(ids.size());
        long relationCount = 0;
        long textBytes = jdbc.queryForObject("SELECT coalesce(sum(octet_length(coalesce(name,''))+octet_length(coalesce(description,''))+octet_length(coalesce(cover_image_url,''))),0) FROM " + table + " WHERE id IN (" + in + ")", Long.class, ids.toArray());
        for (String relation : List.of("alias", "company_relation", "person_relation", "tag_relation", "external_link", "series_item")) {
            List<Object> args = new ArrayList<>();
            args.add(relation.equals("series_item") ? type.toLowerCase(Locale.ROOT) : type); args.addAll(ids);
            String where = relation.equals("series_item") ? "work_type=? AND work_id" : "target_type=? AND target_id";
            relationCount += jdbc.queryForObject("SELECT count(*) FROM " + relation + " WHERE " + where + " IN (" + in + ")", Long.class, args.toArray());
            if (relationCount > RELATION_LIMIT) throw new BusinessException("导出关联超过 10000 条，请缩小范围");
            String fields = switch (relation) {
                case "alias" -> "octet_length(coalesce(alias_name,''))";
                case "company_relation", "person_relation" -> "octet_length(coalesce(role,''))";
                case "external_link" -> "octet_length(coalesce(title,''))+octet_length(coalesce(url,''))";
                default -> "0";
            };
            textBytes += jdbc.queryForObject("SELECT coalesce(sum(" + fields + "),0) FROM " + relation + " WHERE " + where + " IN (" + in + ")", Long.class, args.toArray());
            if (textBytes > FILE_LIMIT) throw new BusinessException("导出文本超过 5 MiB，请缩小范围");
        }
    }

    public ObjectNode preview(String type, String requested, long owner, MultipartFile file) {
        requireType(type, requested);
        Parsed parsed = workbook.read(type, file);
        Problems problems = parsed.problems();
        if (parsed.fileKind().equals("EXPORT")) problems.warning(new Issue("meta", 1L, "fileKind", "", "这是导出文件，确认后新增作品，不更新 sourceId 对应作品；跨库资料 ID 须人工核对"));
        Set<String> names = new HashSet<>();
        List<ImportRow> rows = new ArrayList<>();
        Map<String, Map<Long, String>> previewNames = previewNames(type, parsed.rows());
        long relations = 0;
        for (ImportRow row : parsed.rows()) {
            try {
                Object candidate = type.equals("ANIME") ? json.treeToValue(row.data(), AnimeDTO.class) : json.treeToValue(row.data(), BookDTO.class);
                var violations = validator.validate(candidate);
                if (!violations.isEmpty()) {
                    violations.stream().sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                            .forEach(v -> problems.error(issue(row, v.getPropertyPath().toString(), v.getMessage())));
                    continue;
                }
                verifyReferences(type, row, previewNames);
                checkDisplayNames(row, problems, previewNames);
                ObjectNode normalized = prepare(type, row.data());
                for (String field : List.of("name", "description", "coverImageUrl")) {
                    if (row.data().hasNonNull(field) && !row.data().path(field).asText().equals(normalized.path(field).asText())) {
                        problems.warning(issue(row, field, field + " 已按现有新增规则清洗，请核对实际保存值"));
                    }
                }
                String name = normalized.path("name").asText();
                if (!names.add(name)) {
                    if (type.equals("ANIME")) problems.error(issue(row, "name", "文件内动画名称重复"));
                    else problems.warning(issue(row, "name", "文件内存在同名书籍，将分别新增"));
                }
                if (type.equals("BOOK") && bookMapper.selectCount(new LambdaQueryWrapper<Book>().eq(Book::getName, name)) > 0) problems.warning(issue(row, "name", "已有同名书籍，确认后创建新作品"));
                for (String field : List.of("aliasNames", "companies", "persons", "tagIds", "externalLinks")) {
                    relations += normalized.path(field).size();
                    if (!normalized.path(field).equals(row.data().path(field))) problems.warning(issue(row, field, field + " 已按现有新增规则清洗、去重或填充默认值，请核对预览"));
                }
                rows.add(new ImportRow(row.rowKey(), row.row(), normalized, row.locations()));
            } catch (FieldProblem e) { problems.error(issue(row, e.field, e.getMessage())); }
            catch (BusinessException e) { problems.error(issue(row, "", e.getMessage())); }
        }
        String payload = limitedJson(rows, FILE_LIMIT);
        ObjectNode response = json.createObjectNode().put("targetType", type).put("workCount", parsed.rows().size())
                .put("relationCount", relations).put("errorCount", problems.errorCount).put("warningCount", problems.warningCount)
                .put("truncated", problems.errorCount > 100 || problems.warningCount > 100);
        Map<String, ObjectNode> normalizedRows = new HashMap<>();
        rows.forEach(row -> normalizedRows.put(row.rowKey(), row.data()));
        var responseRows = response.putArray("rows");
        response.set("referenceNames", json.valueToTree(previewNames));
        for (ImportRow row : parsed.rows()) {
            var view = responseRows.addObject().put("rowKey", row.rowKey()).put("row", row.row());
            view.set("data", normalizedRows.getOrDefault(row.rowKey(), row.data()));
        }
        response.set("errors", json.valueToTree(problems.errors)); response.set("warnings", json.valueToTree(problems.warnings));
        if (problems.errorCount > 0) { limitedJson(Result.success(response), RESPONSE_LIMIT); return response; }
        String token = UUID.randomUUID().toString();
        // PostgreSQL timestamptz 保留微秒，缓存与数据库使用完全相同的到期时间。
        Instant expires = Instant.now().plusSeconds(1800).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        String hash = sha256(payload.getBytes(StandardCharsets.UTF_8));
        ObjectNode cached = json.createObjectNode().put("owner", owner).put("targetType", type).put("schemaVersion", 1)
                .put("expiresAt", expires.toString()).put("payloadHash", hash).put("payload", payload);
        cached.set("warnings", json.valueToTree(problems.warnings));
        String cache = limitedJson(cached, RESPONSE_LIMIT);
        response.put("importToken", token).put("expiresAt", expires.toString());
        limitedJson(Result.success(response), RESPONSE_LIMIT);
        try { redis.opsForValue().set(CACHE_PREFIX + token, cache, Duration.ofMinutes(30)); }
        catch (DataAccessException e) { throw new BusinessException("导入预览缓存暂不可用，请稍后重试"); }
        try {
            writeTransaction(() -> {
                jdbc.queryForObject("SELECT 1 FROM pg_advisory_xact_lock(1094928195, ?)", Long.class, (int) (owner ^ (owner >>> 32)));
                Long count = jdbc.queryForObject("SELECT count(*) FROM data_import_batch WHERE account_id=? AND status='READY' AND expires_at>current_timestamp", Long.class, owner);
                if (count != null && count >= 3) throw new BusinessException("最多保留 3 个有效预览，请先确认或取消旧预览");
                jdbc.update("INSERT INTO data_import_batch (import_token,account_id,target_type,schema_version,file_hash,payload_hash,work_count,status,created_at,expires_at) VALUES (?,?,?,1,?,?,?,'READY',current_timestamp,?)",
                        token, owner, type, sha256(fileBytes(file)), hash, rows.size(), OffsetDateTime.ofInstant(expires, ZoneOffset.UTC));
                return null;
            });
        } catch (RuntimeException e) { removeCache(token); throw e; }
        return response;
    }

    private ObjectNode prepare(String type, ObjectNode data) {
        Object dto = type.equals("ANIME") ? json.treeToValue(data, AnimeDTO.class) : json.treeToValue(data, BookDTO.class);
        var violations = validator.validate(dto);
        if (!violations.isEmpty()) {
            var violation = violations.stream().sorted(Comparator.comparing(v -> v.getPropertyPath().toString())).findFirst().orElseThrow();
            throw new FieldProblem(violation.getPropertyPath().toString(), violation.getMessage());
        }
        if (type.equals("ANIME")) {
            int index = 0;
            for (JsonNode link : data.path("externalLinks")) {
                if (link.path("title").asText("").isBlank()) throw new FieldProblem("externalLinks[" + index + "].title", "动画外链标题不能为空");
                index++;
            }
        }
        Object clean = dto instanceof AnimeDTO anime ? animeService.prepareAnime(anime) : bookService.prepareBook((BookDTO) dto);
        ObjectNode normalized = (ObjectNode) json.valueToTree(clean);
        normalized.remove("id"); normalized.put("autoCreateSeries", false);
        return normalized;
    }

    private static class FieldProblem extends BusinessException {
        final String field;
        FieldProblem(String field, String message) { super(message); this.field = field; }
    }

    private Issue issue(ImportRow row, String field, String message) {
        String safe = message == null ? "资料校验失败" : message.substring(0, Math.min(message.length(), 300));
        Issue location = row.locations().get(field);
        return location == null ? new Issue("works", row.row(), field, row.rowKey(), safe)
                : new Issue(location.sheet(), location.row(), location.field(), row.rowKey(), safe);
    }

    private void verifyReferences(String type, ImportRow row) {
        verifyReferences(type, row, null);
    }

    private void verifyReferences(String type, ImportRow row, Map<String, Map<Long, String>> known) {
        ObjectNode data = row.data();
        Map<String, String> references = new LinkedHashMap<>();
        references.put("adaptationTypeId", "adaptation_type"); references.put("regionId", "region"); references.put("seriesId", "series");
        references.put(type.equals("ANIME") ? "broadcastTypeId" : "bookTypeId", type.equals("ANIME") ? "broadcast_type" : "book_type");
        references.forEach((field, table) -> {
            if (data.hasNonNull(field)) requireReference(row, field, table, data.get(field).asLong(), known);
        });
        for (String property : List.of("companies", "persons", "tagIds")) {
            String field = property.equals("companies") ? "companyId" : property.equals("persons") ? "personId" : "tagId";
            String table = property.equals("companies") ? "company" : property.equals("persons") ? "person" : "tag";
            int i = 0;
            for (JsonNode entry : data.path(property)) {
                long id = property.equals("tagIds") ? entry.asLong() : entry.path(field).asLong();
                requireReference(row, property + "[" + i + "]." + field, table, id, known); i++;
            }
        }
    }

    private void requireReference(ImportRow row, String field, String table, long id, Map<String, Map<Long, String>> known) {
        if (id < 1 || id > 2147483647L || (known == null
                ? jdbc.queryForList("SELECT id FROM " + table + " WHERE id=? FOR KEY SHARE", Long.class, id).isEmpty()
                : !known.getOrDefault(table, Map.of()).containsKey(id))) throw new FieldProblem(field, "所选资料 ID 不存在或不在允许范围内");
    }

    private String referenceName(String table, long id) {
        List<String> names = jdbc.queryForList("SELECT name FROM " + table + " WHERE id=?", String.class, id);
        return names.isEmpty() ? "" : names.getFirst();
    }

    /** 辅助名称仅作核对提醒，始终按 ID 引用，绝不按名称自动映射。 */
    private void checkDisplayNames(ImportRow row, Problems problems, Map<String, Map<Long, String>> previewNames) {
        for (String property : List.of("companies", "persons", "tagIds")) {
            String field = property.equals("companies") ? "companyId" : property.equals("persons") ? "personId" : "tagId";
            String table = property.equals("companies") ? "company" : property.equals("persons") ? "person" : "tag";
            int index = 0;
            for (JsonNode item : row.data().path(property)) {
                Issue source = row.locations().get(property + "[" + index + "]._displayName");
                long id = property.equals("tagIds") ? item.asLong() : item.path(field).asLong();
                if (source != null && !source.message().equals(previewNames.getOrDefault(table, Map.of()).getOrDefault(id, ""))) {
                    problems.warning(new Issue(source.sheet(), source.row(), source.field(), row.rowKey(), "辅助名称与当前资料不一致，仍按 ID " + id + " 引用，请核对"));
                }
                index++;
            }
        }
    }

    /** 批量读取本文件引用的辅助名称，不扫描整库，避免预览按关联行逐个查名称。 */
    private Map<String, Map<Long, String>> previewNames(String type, List<ImportRow> rows) {
        Map<String, Set<Long>> ids = new LinkedHashMap<>();
        Map<String, String> basic = Map.of("adaptationTypeId", "adaptation_type", "regionId", "region", "seriesId", "series",
                type.equals("ANIME") ? "broadcastTypeId" : "bookTypeId", type.equals("ANIME") ? "broadcast_type" : "book_type");
        for (ImportRow row : rows) {
            basic.forEach((field, table) -> {
                long id = row.data().path(field).asLong();
                if (id > 0) ids.computeIfAbsent(table, key -> new HashSet<>()).add(id);
            });
            for (String property : List.of("companies", "persons", "tagIds")) {
                String table = property.equals("companies") ? "company" : property.equals("persons") ? "person" : "tag";
                String field = property.equals("companies") ? "companyId" : "personId";
                for (JsonNode entry : row.data().path(property)) {
                    long id = property.equals("tagIds") ? entry.asLong() : entry.path(field).asLong();
                    if (id > 0) ids.computeIfAbsent(table, key -> new HashSet<>()).add(id);
                }
            }
        }
        Map<String, Map<Long, String>> result = new LinkedHashMap<>();
        ids.forEach((table, selected) -> {
            Map<Long, String> names = new LinkedHashMap<>();
            jdbc.query("SELECT id,name FROM " + table + " WHERE id IN (" + placeholders(selected.size()) + ")", rs -> {
                names.put(rs.getLong("id"), rs.getString("name"));
            }, selected.toArray());
            result.put(table, names);
        });
        return result;
    }

    public ObjectNode commit(String type, JsonNode request, long owner) {
        requireType(type, request.path("targetType").asText());
        if (!request.path("acknowledged").isBoolean() || !request.path("acknowledged").asBoolean()) throw new BusinessException("请先确认已核对，将整批新增且不修改原作品");
        String token = token(request.path("importToken").asText());
        TransactionTemplate transaction = transaction(false);
        return transaction.execute(status -> {
            Batch batch = load(token, true, false);
            authorize(batch, owner, type);
            if (!batch.state().equals("READY")) return view(batch);
            if (!batch.expiresAt().isAfter(Instant.now())) return terminal(batch, "EXPIRED", "预览已过期，请先核对已有结果后重新上传", null);
            String raw;
            try { raw = redis.opsForValue().get(CACHE_PREFIX + token); }
            catch (DataAccessException e) { throw new BusinessException("预览缓存服务暂不可用，尚未开始写入，可使用同一 token 重试"); }
            if (raw == null) return terminal(batch, "INVALID", "预览缓存已丢失，请重新上传预览", null);
            List<ImportRow> rows;
            try {
                if (raw.getBytes(StandardCharsets.UTF_8).length > RESPONSE_LIMIT) throw new IllegalArgumentException();
                JsonNode cache = json.readTree(raw);
                String payload = cache.path("payload").asText();
                if (!cache.path("owner").isIntegralNumber() || !cache.path("owner").canConvertToLong()
                        || cache.path("owner").asLong() != owner || !type.equals(cache.path("targetType").asText())
                        || !cache.path("schemaVersion").isIntegralNumber() || !cache.path("schemaVersion").canConvertToInt()
                        || cache.path("schemaVersion").asInt() != 1 || !batch.expiresAt().equals(Instant.parse(cache.path("expiresAt").asText()))
                        || !batch.payloadHash().equals(cache.path("payloadHash").asText()) || !batch.payloadHash().equals(sha256(payload.getBytes(StandardCharsets.UTF_8)))
                        || payload.getBytes(StandardCharsets.UTF_8).length > FILE_LIMIT) throw new IllegalArgumentException();
                rows = json.readValue(payload, json.getTypeFactory().constructCollectionType(List.class, ImportRow.class));
                if (rows.size() != batch.workCount() || rows.isEmpty() || rows.size() > 500) throw new IllegalArgumentException();
            } catch (RuntimeException e) { return terminal(batch, "INVALID", "预览绑定或摘要校验失败，请重新上传", null); }
            if (type.equals("ANIME")) animeMapper.lockAnimeNames();
            Object savepoint = status.createSavepoint();
            String rowKey = "";
            try {
                ObjectNode mappings = json.createObjectNode();
                for (ImportRow row : rows) {
                    rowKey = row.rowKey();
                    verifyReferences(type, row);
                    ObjectNode clean = prepare(type, row.data());
                    // 已确认的默认值及清洗结果不允许在提交时悄悄改变。
                    if (!json.writeValueAsString(clean).equals(json.writeValueAsString(row.data()))) throw new BusinessException("资料标准化结果已变化，请重新预览");
                    long id = type.equals("ANIME") ? animeService.createAnimeInTransaction(json.treeToValue(clean, AnimeDTO.class)).getId()
                            : bookService.createBookInTransaction(json.treeToValue(clean, BookDTO.class)).getId();
                    mappings.put(rowKey, id);
                }
                ObjectNode result = base(batch, "SUCCEEDED").put("createdCount", rows.size());
                result.set("mappings", mappings);
                String resultJson = limitedJson(result, RESPONSE_LIMIT);
                jdbc.update("UPDATE data_import_batch SET status='SUCCEEDED',result=cast(? as jsonb),completed_at=current_timestamp WHERE import_token=?", resultJson, token);
                status.releaseSavepoint(savepoint);
                clearAfterCommit(token);
                return result;
            } catch (BusinessException | DataIntegrityViolationException e) {
                status.rollbackToSavepoint(savepoint);
                status.releaseSavepoint(savepoint);
                sessions.clearCache();
                String reason = e instanceof BusinessException ? e.getMessage() : "资料约束已变化，请重新预览";
                return terminal(batch, "INVALID", reason, rowKey);
            }
        });
    }

    public ObjectNode batch(String type, String requested, String token, long owner, boolean cancel) {
        requireType(type, requested);
        String validToken = token(token);
        return transaction(false).execute(status -> {
            Batch before = load(validToken, false, false);
            authorize(before, owner, type);
            Batch batch;
            Object point = status.createSavepoint();
            try { batch = load(validToken, true, !cancel); status.releaseSavepoint(point); }
            catch (CannotAcquireLockException e) {
                status.rollbackToSavepoint(point); status.releaseSavepoint(point);
                return base(before, "PROCESSING").put("message", "提交结果处理中，请查询或使用同一 token 重试");
            }
            if (!batch.state().equals("READY")) return view(batch);
            if (!batch.expiresAt().isAfter(Instant.now())) return terminal(batch, "EXPIRED", "预览已过期，请先核对已有结果后重新预览", null);
            if (cancel) return terminal(batch, "CANCELLED", "预览已取消，未修改作品", null);
            try {
                if (redis.opsForValue().get(CACHE_PREFIX + validToken) == null) return terminal(batch, "INVALID", "预览缓存已丢失，请重新上传", null);
            } catch (DataAccessException e) { return base(batch, "READY").put("retryable", true).put("message", "预览缓存服务暂不可用，请稍后查询"); }
            return view(batch);
        });
    }

    private Batch load(String token, boolean lock, boolean nowait) {
        List<Batch> result = jdbc.query("SELECT * FROM data_import_batch WHERE import_token=?" + (lock ? " FOR UPDATE" + (nowait ? " NOWAIT" : "") : ""),
                (rs, n) -> new Batch(rs.getString("import_token"), rs.getLong("account_id"), rs.getString("target_type"), rs.getString("status"),
                        rs.getString("payload_hash"), rs.getObject("expires_at", OffsetDateTime.class).toInstant(), rs.getString("result"), rs.getString("reason"), rs.getLong("work_count")), token);
        if (result.isEmpty()) throw new BusinessException("批次不存在或已过保留期，请先核对作品，不能据此判断未执行");
        return result.getFirst();
    }

    private void authorize(Batch batch, long owner, String type) {
        if (batch.owner() != owner) throw new BusinessException("无法访问该批次");
        if (!batch.type().equals(type)) throw new BusinessException("批次类型与当前接口不一致");
    }

    private ObjectNode terminal(Batch batch, String state, String reason, String rowKey) {
        String safe = reason == null ? "" : reason.substring(0, Math.min(300, reason.length()));
        ObjectNode result = base(batch, state).put("message", safe);
        if (rowKey != null) result.put("rowKey", rowKey);
        jdbc.update("UPDATE data_import_batch SET status=?,reason=?,result=cast(? as jsonb),completed_at=current_timestamp WHERE import_token=?", state, safe, json.writeValueAsString(result), batch.token());
        clearAfterCommit(batch.token());
        return result;
    }

    private ObjectNode base(Batch batch, String state) {
        return json.createObjectNode().put("importToken", batch.token()).put("targetType", batch.type())
                .put("status", state).put("expiresAt", batch.expiresAt().toString()).put("workCount", batch.workCount());
    }

    private ObjectNode view(Batch batch) {
        if (batch.result() != null) return (ObjectNode) json.readTree(batch.result());
        return base(batch, batch.state());
    }

    private void clearAfterCommit(String token) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { removeCache(token); }
        });
    }

    private void removeCache(String token) {
        try { redis.delete(CACHE_PREFIX + token); }
        catch (DataAccessException e) { log.warn("导入预览缓存清理暂不可用，保留数据库终态并等待缓存 TTL"); }
    }

    /** 小批清理终态及到期预览，与提交使用同一行锁；不触碰作品或图片。 */
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void cleanup() {
        try {
            writeTransaction(() -> {
                List<String> tokens = jdbc.queryForList("SELECT import_token FROM data_import_batch WHERE status='READY' AND expires_at<=current_timestamp ORDER BY expires_at LIMIT 50 FOR UPDATE SKIP LOCKED", String.class);
                for (String token : tokens) terminal(load(token, false, false), "EXPIRED", "预览已过期，请核对已有结果", null);
                jdbc.update("DELETE FROM data_import_batch WHERE import_token IN (SELECT import_token FROM data_import_batch WHERE status<>'READY' AND completed_at<current_timestamp-interval '7 days' LIMIT 50 FOR UPDATE SKIP LOCKED)");
                return null;
            });
        } catch (DataAccessException e) { log.warn("导入批次清理暂不可用，请检查增量表及数据库连接"); }
    }

    private TransactionTemplate transaction(boolean read) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setReadOnly(read);
        template.setIsolationLevel(read ? TransactionDefinition.ISOLATION_REPEATABLE_READ : TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setTimeout(30);
        return template;
    }
    private <T> T readTransaction(Supplier<T> action) { return transaction(true).execute(status -> action.get()); }
    private <T> T writeTransaction(Supplier<T> action) { return transaction(false).execute(status -> action.get()); }
    private String placeholders(int count) { return String.join(",", Collections.nCopies(count, "?")); }
    private String token(String token) {
        try { if (!UUID.fromString(token).toString().equals(token)) throw new IllegalArgumentException(); return token; }
        catch (IllegalArgumentException e) { throw new BusinessException("无效的导入批次标识"); }
    }
    private String limitedJson(Object value, int limit) {
        try (LimitedOutput out = new LimitedOutput(limit)) { json.writeValue(out, value); return out.toString(StandardCharsets.UTF_8); }
        catch (java.io.IOException e) { throw new BusinessException("数据序列化失败，请缩小范围后重试"); }
        catch (RuntimeException e) {
            if (e instanceof BusinessException business) throw business;
            throw new BusinessException("数据无法编码或超过大小限制，请缩小范围后重试");
        }
    }
    private byte[] fileBytes(MultipartFile file) {
        try (var input = file.getInputStream()) { return input.readNBytes(FILE_LIMIT + 1); }
        catch (java.io.IOException e) { throw new BusinessException("文件读取失败，请重新上传"); }
    }
    private String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("运行环境不支持 SHA-256", e); }
    }
}
