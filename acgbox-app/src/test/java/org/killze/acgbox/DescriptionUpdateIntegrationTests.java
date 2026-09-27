package org.killze.acgbox;

import org.junit.jupiter.api.Test;
import org.killze.acgbox.dto.content.CompanyDTO;
import org.killze.acgbox.dto.content.SeriesDTO;
import org.killze.acgbox.mapper.CompanyMapper;
import org.killze.acgbox.mapper.SeriesMapper;
import org.killze.acgbox.service.CompanyService;
import org.killze.acgbox.service.SeriesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@Transactional
class DescriptionUpdateIntegrationTests {

    @Autowired
    private CompanyService companyService;

    @Autowired
    private CompanyMapper companyMapper;

    @Autowired
    private SeriesService seriesService;

    @Autowired
    private SeriesMapper seriesMapper;

    @Test
    void clearsCompanyDescription() {
        CompanyDTO dto = new CompanyDTO();
        dto.setName("company-" + UUID.randomUUID());
        dto.setDescription("old description");
        Long id = companyService.createCompany(dto).getId();

        dto.setId(id);
        dto.setDescription(null);
        companyService.updateCompany(dto);

        assertNull(companyMapper.selectById(id).getDescription());
    }

    @Test
    void clearsSeriesDescription() {
        SeriesDTO dto = new SeriesDTO();
        dto.setName("series-" + UUID.randomUUID());
        dto.setDescription("old description");
        Long id = seriesService.createSeries(dto).getId();

        dto.setId(id);
        dto.setDescription(null);
        seriesService.updateSeries(dto);

        assertNull(seriesMapper.selectById(id).getDescription());
    }
}
