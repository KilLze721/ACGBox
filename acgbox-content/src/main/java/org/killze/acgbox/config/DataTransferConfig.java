package org.killze.acgbox.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 只调度短期批次清理，不执行作品导出、导入或 COS 文件清理。 */
@Configuration
@EnableScheduling
public class DataTransferConfig {
}
