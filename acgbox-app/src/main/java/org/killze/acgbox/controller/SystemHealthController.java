package org.killze.acgbox.controller;

import org.killze.acgbox.result.Result;
import org.killze.acgbox.service.SystemHealthService;
import org.killze.acgbox.vo.common.SystemHealthVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端连接检测控制器，访问权限由安全配置限制为管理员。
 */
@RestController
@RequestMapping("/admin/system")
public class SystemHealthController {

    @Autowired
    private SystemHealthService systemHealthService;

    /**
     * 返回最近一次数据库、Redis 和 SMTP 检测结果，依赖异常不改变成功响应格式。
     */
    @GetMapping("/health")
    public Result<SystemHealthVO> getSystemHealth() {
        return Result.success(systemHealthService.getSystemHealth());
    }
}
