package com.bank.aml.controller;

import com.bank.aml.mcp.AmlMcpTools;
import com.bank.aml.model.RiskAssessment;
import com.bank.aml.service.AmlMonitorService;
import com.bank.aml.service.DataLoaderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Demo 4 – AML 监控 REST 接口
 *
 * 提供给 Claude Code MCP 调用的 HTTP 端点，
 * 同时也可以通过浏览器访问查看演示效果。
 *
 * 端点列表：
 *   GET  /aml/dashboard          – 监控仪表盘（风险汇总）
 *   GET  /aml/transactions        – 交易列表
 *   GET  /aml/transactions/risky  – 高风险交易
 *   GET  /aml/account/{id}/risk   – 账户风险分析
 *   GET  /aml/detect/smurfing     – 拆分交易检测
 *   GET  /aml/detect/layering     – 分层转账检测
 *   GET  /aml/report/summary      – 风险汇总报告
 *   POST /aml/reload              – 重新加载数据
 */
@RestController
@RequestMapping("/aml")
public class AmlController {

    @Autowired
    private AmlMonitorService amlMonitorService;

    @Autowired
    private DataLoaderService dataLoaderService;

    @Autowired
    private AmlMcpTools mcpTools;

    /**
     * 监控仪表盘 – 返回全量统计摘要
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard() {
        Map<String, Object> result = new HashMap<>();
        result.put("service", "AML Monitor Service");
        result.put("demo", "Demo 4 – 反洗钱智能监控系统");
        result.put("totalTransactions", dataLoaderService.getAllTransactions().size());
        result.put("totalAccounts", dataLoaderService.getAllAccounts().size());
        result.put("riskSummary", mcpTools.tool_generate_risk_summary());
        result.put("regulatoryThresholds", mcpTools.tool_get_regulatory_thresholds());
        return ResponseEntity.ok(result);
    }

    /**
     * 获取所有交易（分页）
     */
    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        var all = dataLoaderService.getAllTransactions();
        int start = page * size;
        int end = Math.min(start + size, all.size());
        if (start > all.size()) {
            return ResponseEntity.ok(Map.of("page", page, "size", size, "total", all.size(), "data", java.util.List.of()));
        }
        return ResponseEntity.ok(Map.of(
            "page", page, "size", size, "total", all.size(),
            "data", all.subList(start, end)
        ));
    }

    /**
     * 获取高风险交易
     */
    @GetMapping("/transactions/risky")
    public ResponseEntity<String> getRiskyTransactions(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(mcpTools.tool_query_high_risk_transactions(limit));
    }

    /**
     * 获取大额交易
     */
    @GetMapping("/transactions/large")
    public ResponseEntity<String> getLargeTransactions(
            @RequestParam(defaultValue = "50000") double threshold) {
        return ResponseEntity.ok(mcpTools.tool_query_large_transactions(threshold));
    }

    /**
     * 账户交易查询
     */
    @GetMapping("/account/{accountId}/transactions")
    public ResponseEntity<String> getAccountTransactions(@PathVariable String accountId) {
        return ResponseEntity.ok(mcpTools.tool_query_transactions_by_account(accountId));
    }

    /**
     * 账户风险分析
     */
    @GetMapping("/account/{accountId}/risk")
    public ResponseEntity<String> analyzeAccountRisk(@PathVariable String accountId) {
        return ResponseEntity.ok(mcpTools.tool_analyze_account_risk(accountId));
    }

    /**
     * 拆分交易检测
     */
    @GetMapping("/detect/smurfing")
    public ResponseEntity<String> detectSmurfing(
            @RequestParam String accountId,
            @RequestParam(defaultValue = "24") int hours) {
        return ResponseEntity.ok(mcpTools.tool_detect_smurfing_pattern(accountId, hours));
    }

    /**
     * 分层转账检测
     */
    @GetMapping("/detect/layering")
    public ResponseEntity<String> detectLayering(
            @RequestParam String accountId,
            @RequestParam(defaultValue = "4") int depth) {
        return ResponseEntity.ok(mcpTools.tool_detect_layering_pattern(accountId, depth));
    }

    /**
     * 风险汇总报告
     */
    @GetMapping("/report/summary")
    public ResponseEntity<String> getRiskSummary() {
        return ResponseEntity.ok(mcpTools.tool_generate_risk_summary());
    }

    /**
     * 监管阈值参考
     */
    @GetMapping("/regulatory/thresholds")
    public ResponseEntity<String> getRegulatoryThresholds() {
        return ResponseEntity.ok(mcpTools.tool_get_regulatory_thresholds());
    }

    /**
     * 重新加载 CSV 数据
     */
    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reloadData() {
        dataLoaderService.reloadData();
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "message", "数据重新加载完成",
            "totalTransactions", dataLoaderService.getAllTransactions().size(),
            "totalAccounts", dataLoaderService.getAllAccounts().size()
        ));
    }
}
