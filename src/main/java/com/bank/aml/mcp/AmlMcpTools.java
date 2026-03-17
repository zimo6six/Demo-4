package com.bank.aml.mcp;

import com.bank.aml.model.RiskAssessment;
import com.bank.aml.model.Transaction;
import com.bank.aml.service.AmlMonitorService;
import com.bank.aml.service.DataLoaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Demo 4 – MCP 工具集
 *
 * 这些方法会被 Claude Code 的 MCP 协议调用，
 * 相当于给 Claude 配备了一组"工具"，让它能够：
 *   - 查询数据库
 *   - 执行风险分析
 *   - 生成报告
 *
 * MCP Tool 命名规范：tool_<动词>_<对象>
 * 每个工具方法都有清晰的 Javadoc 说明其用途，
 * Claude 会根据这些说明决定何时调用哪个工具。
 *
 * 演示重点：
 *   Claude 无需知道底层实现，只需看到方法签名和注释
 *   即可智能编排调用顺序，实现复杂的分析流程。
 */
@Component
public class AmlMcpTools {

    private static final Logger log = LoggerFactory.getLogger(AmlMcpTools.class);

    @Autowired
    private AmlMonitorService amlMonitorService;

    @Autowired
    private DataLoaderService dataLoaderService;

    // ============================================================
    // MCP Tool 1: 数据查询工具
    // ============================================================

    /**
     * MCP Tool: query_transactions_by_account
     * 查询指定账户的所有交易记录。
     *
     * @param accountId 账户ID，如 "ACC001"
     * @return 该账户的交易列表（JSON 格式字符串）
     */
    public String tool_query_transactions_by_account(String accountId) {
        log.info("[MCP] tool_query_transactions_by_account called: accountId={}", accountId);
        List<Transaction> txns = dataLoaderService.getTransactionsByAccount(accountId);
        if (txns.isEmpty()) {
            return String.format("{\"accountId\":\"%s\",\"count\":0,\"transactions\":[]}", accountId);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("{\"accountId\":\"%s\",\"count\":%d,\"transactions\":[", accountId, txns.size()));
        for (int i = 0; i < txns.size(); i++) {
            Transaction t = txns.get(i);
            sb.append(String.format(
                "{\"txnId\":\"%s\",\"datetime\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"amount\":%.2f,\"riskFlag\":\"%s\"}",
                t.getTxnId(), t.getDatetime(), t.getFromAccount(), t.getToAccount(),
                t.getAmount().doubleValue(), t.getRiskFlag()
            ));
            if (i < txns.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * MCP Tool: query_high_risk_transactions
     * 查询所有高风险交易（risk_flag = suspicious 或 structuring 或 layering）。
     *
     * @param limit 最多返回条数，0 表示全部
     * @return 高风险交易列表（JSON 格式字符串）
     */
    public String tool_query_high_risk_transactions(int limit) {
        log.info("[MCP] tool_query_high_risk_transactions called: limit={}", limit);
        List<Transaction> risky = dataLoaderService.getAllTransactions().stream()
            .filter(t -> !"normal".equalsIgnoreCase(t.getRiskFlag()))
            .sorted(Comparator.comparing(Transaction::getAmount).reversed())
            .collect(Collectors.toList());

        if (limit > 0 && risky.size() > limit) {
            risky = risky.subList(0, limit);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("{\"count\":%d,\"transactions\":[", risky.size()));
        for (int i = 0; i < risky.size(); i++) {
            Transaction t = risky.get(i);
            sb.append(String.format(
                "{\"txnId\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"amount\":%.2f,\"type\":\"%s\",\"riskFlag\":\"%s\",\"datetime\":\"%s\"}",
                t.getTxnId(), t.getFromAccount(), t.getToAccount(),
                t.getAmount().doubleValue(), t.getTxnType(), t.getRiskFlag(), t.getDatetime()
            ));
            if (i < risky.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    /**
     * MCP Tool: query_large_transactions
     * 查询超过指定金额阈值的大额交易（监管规定：个人单笔 ≥5万，对公 ≥20万 需上报）。
     *
     * @param threshold 金额阈值（元），建议传入 50000.0
     * @return 大额交易列表
     */
    public String tool_query_large_transactions(double threshold) {
        log.info("[MCP] tool_query_large_transactions called: threshold={}", threshold);
        List<Transaction> large = dataLoaderService.getAllTransactions().stream()
            .filter(t -> t.getAmount().compareTo(BigDecimal.valueOf(threshold)) >= 0)
            .sorted(Comparator.comparing(Transaction::getAmount).reversed())
            .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("{\"threshold\":%.0f,\"count\":%d,\"transactions\":[", threshold, large.size()));
        for (int i = 0; i < large.size(); i++) {
            Transaction t = large.get(i);
            sb.append(String.format(
                "{\"txnId\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"amount\":%.2f,\"channel\":\"%s\",\"datetime\":\"%s\"}",
                t.getTxnId(), t.getFromAccount(), t.getToAccount(),
                t.getAmount().doubleValue(), t.getChannel(), t.getDatetime()
            ));
            if (i < large.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    // ============================================================
    // MCP Tool 2: 风险分析工具
    // ============================================================

    /**
     * MCP Tool: analyze_account_risk
     * 对单个账户进行全面风险评估，包括：
     *   - 历史交易频率
     *   - 大额交易占比
     *   - 关联账户风险传递
     *   - 行为模式异常
     *
     * @param accountId 待分析账户ID
     * @return 风险评分和详细分析（JSON）
     */
    public String tool_analyze_account_risk(String accountId) {
        log.info("[MCP] tool_analyze_account_risk called: accountId={}", accountId);
        RiskAssessment assessment = amlMonitorService.assessAccountRisk(accountId);
        if (assessment == null) {
            return String.format("{\"accountId\":\"%s\",\"error\":\"账户不存在\"}", accountId);
        }
        return String.format(
            "{\"accountId\":\"%s\",\"riskScore\":%.1f,\"riskLevel\":\"%s\"," +
            "\"totalTxnCount\":%d,\"suspiciousTxnCount\":%d," +
            "\"largeAmountCount\":%d,\"totalAmount\":%.2f," +
            "\"mainRiskFactors\":%s,\"suggestion\":\"%s\"}",
            assessment.getAccountId(),
            assessment.getRiskScore(),
            assessment.getRiskLevel(),
            assessment.getTotalTxnCount(),
            assessment.getSuspiciousTxnCount(),
            assessment.getLargeAmountCount(),
            assessment.getTotalAmount().doubleValue(),
            toJsonArray(assessment.getMainRiskFactors()),
            assessment.getSuggestion()
        );
    }

    /**
     * MCP Tool: detect_smurfing_pattern
     * 检测"拆分交易"（Smurfing）模式：
     * 将大额资金拆分为多笔小额交易以规避监管报告阈值。
     * 典型特征：同一账户在24小时内多笔 4000-9999 元转账。
     *
     * @param accountId 账户ID
     * @param hours     检测时间窗口（小时）
     * @return 是否存在拆分交易模式及详情
     */
    public String tool_detect_smurfing_pattern(String accountId, int hours) {
        log.info("[MCP] tool_detect_smurfing_pattern called: accountId={}, hours={}", accountId, hours);
        List<Transaction> txns = dataLoaderService.getTransactionsByAccount(accountId);
        List<Transaction> smurfing = txns.stream()
            .filter(t -> t.getAmount().compareTo(BigDecimal.valueOf(4000)) >= 0
                      && t.getAmount().compareTo(BigDecimal.valueOf(9999)) <= 0
                      && !"normal".equalsIgnoreCase(t.getRiskFlag()))
            .collect(Collectors.toList());

        boolean detected = smurfing.size() >= 3;
        double totalSmurfing = smurfing.stream()
            .mapToDouble(t -> t.getAmount().doubleValue()).sum();

        return String.format(
            "{\"accountId\":\"%s\",\"detected\":%b,\"smurfingTxnCount\":%d," +
            "\"totalAmount\":%.2f,\"riskLevel\":\"%s\"," +
            "\"description\":\"%s\"}",
            accountId, detected, smurfing.size(), totalSmurfing,
            detected ? "HIGH" : "LOW",
            detected
                ? "发现拆分交易模式：" + smurfing.size() + "笔交易合计" + String.format("%.0f", totalSmurfing) + "元"
                : "未发现明显拆分交易模式"
        );
    }

    /**
     * MCP Tool: detect_layering_pattern
     * 检测"分层转账"（Layering）模式：
     * 资金经过多个中间账户快速流转以掩盖来源。
     * 典型特征：A→B→C→D 链式转账，单笔间隔 < 2小时。
     *
     * @param startAccountId 起始账户ID
     * @param depth          最大追踪深度（推荐3-5）
     * @return 分层链路详情
     */
    public String tool_detect_layering_pattern(String startAccountId, int depth) {
        log.info("[MCP] tool_detect_layering_pattern called: startAccountId={}, depth={}", startAccountId, depth);
        List<Transaction> chain = amlMonitorService.traceLayeringChain(startAccountId, depth);
        boolean detected = chain.size() >= 3;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("{\"startAccount\":\"%s\",\"detected\":%b,\"chainLength\":%d,\"chain\":[",
            startAccountId, detected, chain.size()));
        for (int i = 0; i < chain.size(); i++) {
            Transaction t = chain.get(i);
            sb.append(String.format("{\"step\":%d,\"txnId\":\"%s\",\"from\":\"%s\",\"to\":\"%s\",\"amount\":%.2f}",
                i + 1, t.getTxnId(), t.getFromAccount(), t.getToAccount(), t.getAmount().doubleValue()));
            if (i < chain.size() - 1) sb.append(",");
        }
        sb.append(String.format("],\"conclusion\":\"%s\"}",
            detected ? "⚠️ 发现分层转账链路，建议上报监管机构" : "未发现明显分层模式"));
        return sb.toString();
    }

    // ============================================================
    // MCP Tool 3: 报告生成工具
    // ============================================================

    /**
     * MCP Tool: generate_risk_summary
     * 生成全量风险汇总统计，供 Claude 撰写合规报告时使用。
     *
     * @return 风险汇总统计（JSON）
     */
    public String tool_generate_risk_summary() {
        log.info("[MCP] tool_generate_risk_summary called");
        List<Transaction> all = dataLoaderService.getAllTransactions();
        long total = all.size();
        long suspicious = all.stream().filter(t -> "suspicious".equalsIgnoreCase(t.getRiskFlag())).count();
        long structuring = all.stream().filter(t -> "structuring".equalsIgnoreCase(t.getRiskFlag())).count();
        long layering = all.stream().filter(t -> "layering".equalsIgnoreCase(t.getRiskFlag())).count();
        long largeNormal = all.stream().filter(t -> "large_normal".equalsIgnoreCase(t.getRiskFlag())).count();
        double totalAmount = all.stream().mapToDouble(t -> t.getAmount().doubleValue()).sum();
        double suspiciousAmount = all.stream()
            .filter(t -> !"normal".equalsIgnoreCase(t.getRiskFlag()) && !"large_normal".equalsIgnoreCase(t.getRiskFlag()))
            .mapToDouble(t -> t.getAmount().doubleValue()).sum();

        return String.format(
            "{\"reportDate\":\"2026-03-17\",\"reportPeriod\":\"2026-01-01 至 2026-03-17\"," +
            "\"totalTransactions\":%d,\"totalAmount\":%.2f," +
            "\"suspiciousCount\":%d,\"structuringCount\":%d,\"layeringCount\":%d," +
            "\"largeNormalCount\":%d,\"suspiciousAmount\":%.2f," +
            "\"suspiciousRatio\":\"%.1f%%\",\"riskDistribution\":{" +
            "\"suspicious\":%d,\"structuring\":%d,\"layering\":%d,\"large_normal\":%d,\"normal\":%d}}",
            total, totalAmount,
            suspicious, structuring, layering, largeNormal, suspiciousAmount,
            (double)(suspicious + structuring + layering) / total * 100,
            suspicious, structuring, layering, largeNormal,
            total - suspicious - structuring - layering - largeNormal
        );
    }

    /**
     * MCP Tool: get_regulatory_thresholds
     * 获取中国反洗钱监管阈值（人民银行规定）。
     * Claude 在分析时需要参考这些阈值判断是否需要上报。
     *
     * @return 监管阈值配置（JSON）
     */
    public String tool_get_regulatory_thresholds() {
        log.info("[MCP] tool_get_regulatory_thresholds called");
        return "{" +
            "\"regulator\":\"中国人民银行\"," +
            "\"rules\":[" +
            "{\"type\":\"大额交易报告\",\"personalThreshold\":50000,\"corporateThreshold\":200000,\"currency\":\"CNY\",\"deadline\":\"T+5工作日\"}," +
            "{\"type\":\"可疑交易报告\",\"trigger\":\"行为异常或无合理理由\",\"deadline\":\"T+10工作日\"}," +
            "{\"type\":\"现金交易报告\",\"threshold\":10000,\"currency\":\"CNY\",\"deadline\":\"T+3工作日\"}," +
            "{\"type\":\"跨境交易报告\",\"threshold\":1000,\"currency\":\"USD\",\"deadline\":\"T+5工作日\"}" +
            "]," +
            "\"penalties\":{\"missedReport\":\"5万-50万罚款\",\"falseReport\":\"直接负责人双罚\"}," +
            "\"lastUpdated\":\"2022-08-01\"" +
            "}";
    }

    // ============================================================
    // 工具辅助方法
    // ============================================================

    private String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            sb.append("\"").append(list.get(i)).append("\"");
            if (i < list.size() - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}
