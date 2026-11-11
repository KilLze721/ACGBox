package org.killze.acgbox.service.impl;

import org.killze.acgbox.dto.content.DataQualityPageDTO;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.vo.content.DataQualityPageVO;
import org.killze.acgbox.vo.content.DataQualityPageVO.Issue;
import org.killze.acgbox.vo.content.DataQualityPageVO.Reference;
import org.killze.acgbox.vo.content.DataQualityPageVO.Row;
import org.killze.acgbox.vo.content.DataQualityPageVO.Summary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;

/** 实时读取内容资料，不持久化检查结果。 */
@Service
public class DataQualityServiceImpl {
    private static final String WHITESPACE = " \t\n\r\f" + (char) 11 + (char) 0xa0 + (char) 0x3000;
    private static final String SQL = """
            WITH named AS (
                SELECT w.id, w.name, w.cover_image_url, w.description, w.updated_at, w.region_id,
                       w.%1$s AS type_id, w.%5$s AS work_date,
                       COUNT(*) OVER (PARTITION BY %2$s
                           translate(btrim(w.name, ?), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz') COLLATE "C") AS duplicate_count
                FROM %3$s w
            ), scoped AS (
                SELECT id, name, cover_image_url, updated_at, region_id, type_id, duplicate_count,
                       CASE WHEN COALESCE(btrim(cover_image_url, ?), '') = '' THEN 1 ELSE 0 END AS missing_cover,
                       CASE WHEN COALESCE(btrim(description, ?), '') = '' THEN 1 ELSE 0 END AS missing_description,
                       CASE WHEN NOT EXISTS (
                           SELECT 1 FROM person_relation pr WHERE pr.target_type = ? AND pr.target_id = named.id
                       ) THEN 1 ELSE 0 END AS missing_person,
                       CASE WHEN NOT EXISTS (
                           SELECT 1 FROM company_relation cr WHERE cr.target_type = ? AND cr.target_id = named.id
                       ) THEN 1 ELSE 0 END AS missing_company,
                       CASE WHEN NOT EXISTS (
                           SELECT 1 FROM tag_relation tr WHERE tr.target_type = ? AND tr.target_id = named.id
                       ) THEN 1 ELSE 0 END AS missing_tag,
                       CASE WHEN work_date IS NULL THEN 1 ELSE 0 END AS missing_date,
                       CASE WHEN duplicate_count > 1 THEN 1 ELSE 0 END AS same_name_review
                FROM named
                WHERE strpos(translate(name, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'),
                             translate(btrim(?, ?), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz')) > 0
            ), checked AS (
                SELECT scoped.*, missing_cover * ? + missing_description * ? + missing_person * ?
                    + missing_company * ? + missing_tag * ? + missing_date * ? + same_name_review * ? AS selected_hits
                FROM scoped
            ), summary AS (
                SELECT COUNT(*) AS scope_total,
                       COALESCE(SUM(missing_cover), 0) AS count_cover,
                       COALESCE(SUM(missing_description), 0) AS count_description,
                       COALESCE(SUM(missing_person), 0) AS count_person,
                       COALESCE(SUM(missing_company), 0) AS count_company,
                       COALESCE(SUM(missing_tag), 0) AS count_tag,
                       COALESCE(SUM(missing_date), 0) AS count_date,
                       COALESCE(SUM(same_name_review), 0) AS count_same_name,
                       COALESCE(SUM(CASE WHEN selected_hits >= ? THEN 1 ELSE 0 END), 0) AS total
                FROM checked
            ), paged AS (
                SELECT * FROM checked WHERE selected_hits >= ?
                ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?
            )
            SELECT summary.*, paged.*, ct.id AS content_type_id, ct.name AS content_type_name,
                   r.id AS region_ref_id, r.name AS region_name
            FROM summary LEFT JOIN paged ON TRUE
            LEFT JOIN %4$s ct ON ct.id = paged.type_id
            LEFT JOIN region r ON r.id = paged.region_id
            ORDER BY paged.updated_at DESC, paged.id DESC
            """;

    // 公司和系列复用人物的同名检查，只替换白名单内的表名。
    private static final String PERSON_SQL = """
            WITH named AS (
                SELECT id, name, description, updated_at,
                       COUNT(*) OVER (PARTITION BY
                           translate(btrim(name, ?), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz') COLLATE "C") AS duplicate_count
                FROM %s
            ), scoped AS (
                SELECT *
                FROM named
                WHERE strpos(translate(name, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'),
                             translate(btrim(?, ?), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz')) > 0
            ), summary AS (
                SELECT COUNT(*) AS scope_total,
                       COUNT(CASE WHEN duplicate_count > 1 THEN 1 END) AS count_same_name
                FROM scoped
            ), paged AS (
                SELECT scoped.*, 1 AS same_name_review FROM scoped WHERE duplicate_count > 1
                ORDER BY updated_at DESC, id DESC LIMIT ? OFFSET ?
            )
            SELECT summary.*, summary.count_same_name AS total, paged.*
            FROM summary LEFT JOIN paged ON TRUE
            ORDER BY paged.updated_at DESC, paged.id DESC
            """;

    private enum Rule {
        MISSING_COVER("缺封面", "missing_cover", "count_cover"),
        MISSING_DESCRIPTION("缺简介", "missing_description", "count_description"),
        MISSING_PERSON("缺创作人员", "missing_person", "count_person"),
        MISSING_COMPANY("缺公司", "missing_company", "count_company"),
        MISSING_TAG("缺标签", "missing_tag", "count_tag"),
        MISSING_DATE("缺日期", "missing_date", "count_date"),
        SAME_NAME_REVIEW("同名待核对", "same_name_review", "count_same_name");

        final String message;
        final String hitColumn;
        final String countColumn;

        Rule(String message, String hitColumn, String countColumn) {
            this.message = message;
            this.hitColumn = hitColumn;
            this.countColumn = countColumn;
        }
    }

    private final JdbcTemplate jdbc;

    public DataQualityServiceImpl(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DataQualityPageVO pageDataQuality(DataQualityPageDTO dto) {
        String target = dto.getTargetType();
        boolean anime = "ANIME".equals(target);
        String catalogTable = switch (target == null ? "" : target) {
            case "PERSON" -> "person";
            case "COMPANY" -> "company";
            case "SERIES" -> "series";
            default -> null;
        };
        if (!anime && catalogTable == null && !"BOOK".equals(target)) {
            throw new BusinessException("targetType 必须为 ANIME、BOOK、PERSON、COMPANY 或 SERIES");
        }
        String mode = dto.getMatchMode();
        if (!"ANY".equals(mode) && !"ALL".equals(mode)) throw new BusinessException("matchMode 必须为 ANY 或 ALL");
        if (dto.getRuleCodes() == null || dto.getRuleCodes().isEmpty()) throw new BusinessException("至少选择一项检查规则");
        var rules = EnumSet.noneOf(Rule.class);
        for (String code : dto.getRuleCodes()) {
            try {
                rules.add(Rule.valueOf(code));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new BusinessException("ruleCodes 包含未知检查规则");
            }
        }
        if (catalogTable != null && !rules.equals(EnumSet.of(Rule.SAME_NAME_REVIEW))) {
            throw new BusinessException(target + " 仅支持 SAME_NAME_REVIEW 检查规则");
        }
        Long pageNum = dto.getPageNum();
        Long pageSize = dto.getPageSize();
        if (pageNum == null || pageNum < 1) throw new BusinessException("pageNum 必须大于等于 1");
        if (pageSize == null || pageSize < 1 || pageSize > 100) throw new BusinessException("pageSize 必须在 1 到 100 之间");
        long offset = pageNum - 1 > Long.MAX_VALUE / pageSize ? Long.MAX_VALUE : (pageNum - 1) * pageSize;
        String keyword = dto.getKeyword() == null ? "" : dto.getKeyword();
        // 单条查询共享同一读取视图；汇总行保证空结果和超页仍有统计。
        ResultSetExtractor<DataQualityPageVO> result = rs -> assemble(rs, target, rules, pageNum, pageSize);
        if (catalogTable != null) {
            return jdbc.query(PERSON_SQL.formatted(catalogTable), result, WHITESPACE, keyword, WHITESPACE, pageSize, offset);
        }
        int minimum = "ALL".equals(mode) ? rules.size() : 1;
        String sql = anime ? SQL.formatted("broadcast_type_id", "", "anime", "broadcast_type", "air_date")
                : SQL.formatted("book_type_id", "w.book_type_id, ", "book", "book_type", "publish_date");
        return jdbc.query(sql, result,
                WHITESPACE, WHITESPACE, WHITESPACE, target, target, target, keyword, WHITESPACE,
                rules.contains(Rule.MISSING_COVER) ? 1 : 0,
                rules.contains(Rule.MISSING_DESCRIPTION) ? 1 : 0,
                rules.contains(Rule.MISSING_PERSON) ? 1 : 0,
                rules.contains(Rule.MISSING_COMPANY) ? 1 : 0,
                rules.contains(Rule.MISSING_TAG) ? 1 : 0,
                rules.contains(Rule.MISSING_DATE) ? 1 : 0,
                rules.contains(Rule.SAME_NAME_REVIEW) ? 1 : 0,
                minimum, minimum, pageSize, offset);
    }

    private DataQualityPageVO assemble(ResultSet rs, String target, EnumSet<Rule> rules,
                                       long pageNum, long pageSize) throws SQLException {
        if (!rs.next()) throw new IllegalStateException("数据质量检查未返回统计");
        boolean catalog = !"ANIME".equals(target) && !"BOOK".equals(target);
        long total = rs.getLong("total");
        var counts = new LinkedHashMap<String, Long>();
        for (Rule rule : Rule.values()) {
            if (!catalog || rule == Rule.SAME_NAME_REVIEW) counts.put(rule.name(), rs.getLong(rule.countColumn));
        }
        var summary = new Summary(rs.getLong("scope_total"), counts);
        var rows = new ArrayList<Row>();
        do {
            long id = rs.getLong("id");
            if (rs.wasNull()) break;
            var issues = new ArrayList<Issue>();
            Long duplicateCount = null;
            for (Rule rule : rules) {
                if (rs.getInt(rule.hitColumn) == 0) continue;
                if (rule == Rule.SAME_NAME_REVIEW) {
                    duplicateCount = rs.getLong("duplicate_count");
                    String message = switch (target) {
                        case "PERSON" -> "同名人物共 " + duplicateCount + " 人，请人工核对";
                        case "COMPANY" -> "同名公司共 " + duplicateCount + " 家，请人工核对";
                        case "SERIES" -> "同名系列共 " + duplicateCount + " 个，请人工核对";
                        default -> "同类型同名作品共 " + duplicateCount + " 部，请人工核对";
                    };
                    issues.add(new Issue(rule.name(), message));
                } else {
                    issues.add(new Issue(rule.name(), rule.message));
                }
            }
            var updatedAt = rs.getTimestamp("updated_at");
            rows.add(new Row(target, id, rs.getString("name"), catalog ? null : rs.getString("cover_image_url"),
                    catalog ? null : reference(rs, "content_type_id", "content_type_name"),
                    catalog ? null : reference(rs, "region_ref_id", "region_name"),
                    updatedAt == null ? null : updatedAt.toLocalDateTime(), issues, duplicateCount,
                    catalog ? rs.getString("description") : null));
        } while (rs.next());
        long pages = total == 0 ? 0 : 1 + (total - 1) / pageSize;
        return new DataQualityPageVO(pageNum, pageSize, total, pages, OffsetDateTime.now(ZoneId.of("Asia/Shanghai")), summary, rows);
    }

    private Reference reference(ResultSet rs, String idColumn, String nameColumn) throws SQLException {
        long id = rs.getLong(idColumn);
        return rs.wasNull() ? null : new Reference(id, rs.getString(nameColumn));
    }
}
