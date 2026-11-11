package org.killze.acgbox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import net.sourceforge.pinyin4j.PinyinHelper;
import org.killze.acgbox.entity.content.Tag;
import org.killze.acgbox.exception.BusinessException;
import org.killze.acgbox.vo.content.TagVO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 公司、人物、系列和标签共用的首字母排序。 */
final class CatalogNameInitialPage {

    private static final int OTHER_INITIAL = 'Z' + 1;

    private CatalogNameInitialPage() {
    }

    static <T> Page<T> selectPage(BaseMapper<T> mapper, Page<T> page, LambdaQueryWrapper<T> wrapper,
                                  SFunction<T, Long> id, SFunction<T, String> name, String sortDirection) {
        boolean descending = switch (sortDirection == null ? "" : sortDirection.trim().toUpperCase(Locale.ROOT)) {
            case "", "ASC" -> false;
            case "DESC" -> true;
            default -> throw new BusinessException("sortDirection 仅支持 ASC 或 DESC");
        };
        if (page.getSize() < 1 || page.getSize() > 100) {
            throw new BusinessException("pageSize 必须处于 1 到 100 之间");
        }
        // 轻量结果已按更新时间和 ID 降序，同组保持该顺序。
        var keys = mapper.selectList(wrapper.select(List.of(id, name)));
        page.setTotal(keys.size());
        if (page.getCurrent() > page.getPages()) {
            return page;
        }
        var groups = new TreeMap<Integer, List<Long>>();
        for (T key : keys) {
            groups.computeIfAbsent(initialOrder(name.apply(key), descending), ignored -> new ArrayList<>()).add(id.apply(key));
        }
        List<Long> ids = groups.values().stream().flatMap(List::stream)
                .skip(page.offset()).limit(page.getSize()).toList();
        var records = mapper.selectByIds(ids).stream().collect(Collectors.toMap(id, Function.identity()));
        page.setRecords(ids.stream().map(records::get).filter(Objects::nonNull).toList());
        return page;
    }

    static List<TagVO> tagViews(Stream<Tag> tags) {
        return tags.sorted(Comparator.comparingInt((Tag tag) -> initialOrder(tag.getName(), true))
                        .thenComparing(Tag::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Tag::getId, Comparator.reverseOrder()))
                .map(tag -> TagVO.builder().id(tag.getId()).name(tag.getName()).build())
                .toList();
    }

    private static int initialOrder(String name, boolean descending) {
        int letter = initial(name);
        // 倒序只反转字母组，非字母组仍置于末尾。
        return descending && letter != OTHER_INITIAL ? 'A' + 'Z' - letter : letter;
    }

    private static int initial(String name) {
        int first = name.codePoints().dropWhile(cp -> Character.isWhitespace(cp) || Character.isSpaceChar(cp))
                .findFirst().orElse(-1);
        if (first >= 0 && Character.isBmpCodePoint(first)
                && Character.UnicodeScript.of(first) == Character.UnicodeScript.HAN) {
            String[] readings = PinyinHelper.toHanyuPinyinStringArray((char) first);
            if (readings != null && readings.length > 0) {
                first = readings[0].charAt(0);
            }
        }
        if (first >= 'a' && first <= 'z') {
            first -= 'a' - 'A';
        }
        // 末尾一组用大于 Z 的排序键表示，不额外返回分组字段。
        return first >= 'A' && first <= 'Z' ? first : OTHER_INITIAL;
    }
}
