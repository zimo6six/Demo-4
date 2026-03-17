package com.bank.aml;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Demo 4 – 反洗钱智能监控系统
 *
 * 演示目标：展示 Claude Code 通过 MCP 工具调用数据库、执行风险分析、
 * 生成合规报告的完整 AI Agent 工作流。
 *
 * 核心能力：
 *   1. 读取 CSV 交易数据并入库
 *   2. 规则引擎检测可疑交易（分层、拆分、循环转账）
 *   3. MCP 工具暴露 SQL 查询能力给 Claude
 *   4. Claude 生成中文合规报告
 *
 * @author Claude Code Demo
 * @version 1.0.0
 */
@SpringBootApplication
@EnableScheduling
public class AmlApplication {

    public static void main(String[] args) {
        SpringApplication.run(AmlApplication.class, args);
        System.out.println("===========================================");
        System.out.println("  AML Monitor Service Started");
        System.out.println("  Demo 4 – 反洗钱智能监控系统");
        System.out.println("  http://localhost:8082/aml/dashboard");
        System.out.println("===========================================");
    }
}
