package org.killze.acgbox.controller;

import org.killze.acgbox.dto.content.DataQualityPageDTO;
import org.killze.acgbox.result.Result;
import org.killze.acgbox.service.impl.DataQualityServiceImpl;
import org.killze.acgbox.vo.content.DataQualityPageVO;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.beans.PropertyEditorSupport;

/** 内容维护的只读检查接口，沿用现有管理端内容权限。 */
@RestController
@RequestMapping("/data-quality")
public class DataQualityController {
    private final DataQualityServiceImpl dataQualityService;

    public DataQualityController(DataQualityServiceImpl dataQualityService) {
        this.dataQualityService = dataQualityService;
    }

    @InitBinder
    public void bindQualityNumbers(WebDataBinder binder) {
        binder.registerCustomEditor(Long.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(Long.valueOf(text));
            }
        });
    }

    @GetMapping("/page")
    public Result<DataQualityPageVO> pageDataQuality(DataQualityPageDTO dto) {
        return Result.success(dataQualityService.pageDataQuality(dto));
    }
}
