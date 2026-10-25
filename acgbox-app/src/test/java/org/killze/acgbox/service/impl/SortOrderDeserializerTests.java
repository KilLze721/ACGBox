package org.killze.acgbox.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.killze.acgbox.dto.content.AdaptationTypeDTO;
import org.killze.acgbox.dto.content.AnimeDTO;
import org.killze.acgbox.dto.content.BroadcastTypeDTO;
import org.killze.acgbox.dto.content.CompanyRelationDTO;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 验证三个排序属性严格绑定整数，且不改变其他字段的默认数字绑定。
 */
class SortOrderDeserializerTests {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "-1.5", "1.0", "1e0", "\"1\"", "true", "{}", "[]"})
    void rejectsNonIntegerTokensInAllThreePropertiesAndNestedCompanies(String value) {
        String json = "{\"sortOrder\":" + value + "}";
        assertThrows(MismatchedInputException.class, () -> mapper.readValue(json, BroadcastTypeDTO.class));
        assertThrows(MismatchedInputException.class, () -> mapper.readValue(json, AdaptationTypeDTO.class));
        assertThrows(MismatchedInputException.class, () -> mapper.readValue(json, CompanyRelationDTO.class));
        assertThrows(MismatchedInputException.class, () -> mapper.readValue(
                "{\"companies\":[{\"companyId\":1,\"sortOrder\":" + value + "}]}", AnimeDTO.class));
    }

    @ParameterizedTest
    @ValueSource(longs = {-2147483648L, -1L, 0L, 2147483647L})
    void acceptsNegativeZeroAndBoundaryIntegers(long value) {
        String json = "{\"sortOrder\":" + value + "}";
        assertEquals(value, mapper.readValue(json, BroadcastTypeDTO.class).getSortOrder());
        assertEquals(value, mapper.readValue(json, AdaptationTypeDTO.class).getSortOrder());
        assertEquals(value, mapper.readValue(json, CompanyRelationDTO.class).getSortOrder());
        assertEquals(value, mapper.readValue(
                "{\"companies\":[{\"companyId\":1,\"sortOrder\":" + value + "}]}", AnimeDTO.class)
                .getCompanies().getFirst().getSortOrder());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"sortOrder\":null}"})
    void missingAndNullRemainNullForServiceDefaultHandling(String json) {
        assertNull(mapper.readValue(json, BroadcastTypeDTO.class).getSortOrder());
        assertNull(mapper.readValue(json, AdaptationTypeDTO.class).getSortOrder());
        assertNull(mapper.readValue(json, CompanyRelationDTO.class).getSortOrder());
        assertNull(mapper.readValue("{\"companies\":[" + json + "]}", AnimeDTO.class)
                .getCompanies().getFirst().getSortOrder());
    }

    @Test
    void rejectsIntegerOverflowWithoutWrapping() {
        String json = "{\"sortOrder\":9223372036854775808}";
        assertThrows(JacksonException.class, () -> mapper.readValue(json, BroadcastTypeDTO.class));
        assertThrows(JacksonException.class, () -> mapper.readValue(json, AdaptationTypeDTO.class));
        assertThrows(JacksonException.class, () -> mapper.readValue(json, CompanyRelationDTO.class));
    }

    @Test
    void otherLongFieldsKeepExistingBindingBehavior() {
        assertEquals(1L, mapper.readValue("{\"episodeCount\":1.5}", AnimeDTO.class).getEpisodeCount());
    }
}
