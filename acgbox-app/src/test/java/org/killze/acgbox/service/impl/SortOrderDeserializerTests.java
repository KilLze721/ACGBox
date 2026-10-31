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
 * 验证排序输入的整数约束、空值兼容和溢出拒绝。
 */
class SortOrderDeserializerTests {

    private final JsonMapper mapper = applicationMapper();

    /** 只加载公共 JSON 配置，不启动应用、不读取私有配置或连接外部服务。 */
    private JsonMapper applicationMapper() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            var sources = new org.springframework.boot.env.YamlPropertySourceLoader().load("public-json",
                    new org.springframework.core.io.ClassPathResource("application.yml"));
            sources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
            context.register(org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration.class);
            context.refresh();
            return context.getBean(JsonMapper.class);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @Test
    void globalNumbersKeepStringsAndDecimalScoresWithoutTruncatingIds() {
        assertThrows(JacksonException.class, () -> mapper.readValue("{\"episodeCount\":1.5}", AnimeDTO.class));
        assertEquals(1L, mapper.readValue("{\"episodeCount\":\"1\"}", AnimeDTO.class).getEpisodeCount());
        assertEquals(1L, mapper.readValue("{\"sortOrder\":\"1\"}", BroadcastTypeDTO.class).getSortOrder());
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            for (String score : java.util.List.of("0", "8.0", "10", "8.5")) {
                var book = mapper.readValue("{\"personalRatingScore\":" + score + "}",
                        org.killze.acgbox.dto.content.BookDTO.class);
                assertEquals(0, new java.math.BigDecimal(score).compareTo(book.getPersonalRatingScore()));
                assertEquals(score.equals("8.5"), !factory.getValidator()
                        .validateProperty(book, "personalRatingScore").isEmpty());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "-1.5", "1.0", "1e0", "true", "{}", "[]"})
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

}
