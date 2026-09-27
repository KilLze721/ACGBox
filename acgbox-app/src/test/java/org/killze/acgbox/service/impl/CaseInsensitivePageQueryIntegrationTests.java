package org.killze.acgbox.service.impl;

import org.junit.jupiter.api.Test;
import org.killze.acgbox.dto.content.CompanyDTO;
import org.killze.acgbox.dto.content.CompanyPageDTO;
import org.killze.acgbox.dto.content.SeriesDTO;
import org.killze.acgbox.dto.content.SeriesPageDTO;
import org.killze.acgbox.dto.content.TagDTO;
import org.killze.acgbox.dto.content.TagPageDTO;
import org.killze.acgbox.service.CompanyService;
import org.killze.acgbox.service.SeriesService;
import org.killze.acgbox.service.TagService;
import org.killze.acgbox.vo.content.CompanyVO;
import org.killze.acgbox.vo.content.SeriesVO;
import org.killze.acgbox.vo.content.TagVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 分页名称查询忽略字母大小写的集成测试
 */
@SpringBootTest
@Transactional
class CaseInsensitivePageQueryIntegrationTests {

    @Autowired
    private CompanyService companyService;

    @Autowired
    private SeriesService seriesService;

    @Autowired
    private TagService tagService;

    @Test
    void shouldFindDifferentLetterCasesAndPreserveOriginalNames() {
        String prefix = "CASESEARCH" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        for (String suffix : List.of("ABC", "Abc", "abc")) {
            CompanyDTO companyDTO = new CompanyDTO();
            companyDTO.setName(prefix + suffix);
            companyService.createCompany(companyDTO);

            SeriesDTO seriesDTO = new SeriesDTO();
            seriesDTO.setName(prefix + suffix);
            seriesService.createSeries(seriesDTO);

            TagDTO tagDTO = new TagDTO();
            tagDTO.setName(prefix + suffix);
            tagService.createTag(tagDTO);
        }

        String keyword = (prefix + "abc").toLowerCase(Locale.ROOT);
        Set<String> expectedNames = Set.of(prefix + "ABC", prefix + "Abc", prefix + "abc");

        CompanyPageDTO companyPageDTO = new CompanyPageDTO();
        companyPageDTO.setName(keyword);
        var companies = companyService.pageCompany(companyPageDTO);
        assertEquals(3L, companies.getTotal());
        assertEquals(expectedNames, companies.getRows().stream().map(CompanyVO::getName).collect(Collectors.toSet()));

        SeriesPageDTO seriesPageDTO = new SeriesPageDTO();
        seriesPageDTO.setName(keyword);
        var series = seriesService.pageSeries(seriesPageDTO);
        assertEquals(3L, series.getTotal());
        assertEquals(expectedNames, series.getRows().stream().map(SeriesVO::getName).collect(Collectors.toSet()));

        TagPageDTO tagPageDTO = new TagPageDTO();
        tagPageDTO.setName(keyword);
        var tags = tagService.pageTag(tagPageDTO);
        assertEquals(3L, tags.getTotal());
        assertEquals(expectedNames, tags.getRows().stream().map(TagVO::getName).collect(Collectors.toSet()));
    }
}
