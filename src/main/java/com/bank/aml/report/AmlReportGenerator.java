package com.bank.aml.report;

import com.bank.aml.mcp.AmlMcpTools;
import com.bank.aml.model.Transaction;
import com.bank.aml.service.DataLoaderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Demo 4 – AML 合规报告生成器
 *
 * 提供给 Claude Code 使用的报告模板引擎。
 * Claude 调用 MCP 工具获取数据后，会参考这里的格式生成最终报告。
 *
 * 报告类型：
 *   1. 大额交易报告（LARD）
 *   2. 可疑交易报告（SAR）
 *   3. 月度 AML 汇总报告
 */
@Component
public class AmlReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(AmlReportGenerator.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private DataLoaderService dataLoaderService;

    @Autowired
    private AmlMcpTools mcpTools;

    /**
     * 生成大额交易报告（Large Amount Report）
     * 格式符合中国人民银行《大额交易报告管理办法》
     *
     * @param threshold 金额阈值（元）
     * @return 格式化报告文本
     */
    public String generateLargeAmountReport(double threshold) {
        log.info("生成大额交易报告，阈值：{}元", threshold);

        List<Transaction> largeTxns = dataLoaderService.getAllTransactions().stream()
            .filter(t -> t.getAmount().doubleValue() >= threshold)
            .sorted((a, b) -> b.getAmount().compareTo(a.getAmount()))
            .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════════\n");
        sb.append("                  大额交易报告（LARD）\n");
        sb.append("═══════════════════════════════════════════════════════════\n");
        sb.append(String.format("报告日期：%s\n", LocalDateTime.now().format(FMT)));
        sb.append(String.format("报告机构：示例银行（DEMO）\n"));
        sb.append(String.format("统计期间：2026-01-01 至 2026-03-17\n"));
        sb.append(String.format("阈值标准：个人单笔 ≥ %.0f 元\n", threshold));
        sb.append("───────────────────────────────────────────────────────────\n");
        sb.append(String.format("共发现 %d 笔大额交易\n\n", largeTxns.size()));

        for (int i = 0; i < Math.min(largeTxns.size(), 20); i++) {
            Transaction t = largeTxns.get(i);
            sb.append(String.format("  [%02d] %s  %s → %s  金额：%,.2f 元  渠道：%s\n",
                i + 1, t.getTxnId(),
                t.getFromAccount(), t.getToAccount(),
                t.getAmount().doubleValue(), t.getChannel()));
        }
        if (largeTxns.size() > 20) {
            sb.append(String.format("  ... 共 %d 笔，仅显示前20笔\n", largeTxns.size()));
        }

        double totalAmount = largeTxns.stream().mapToDouble(t -> t.getAmount().doubleValue()).sum();
        sb.append("───────────────────────────────────────────────────────────\n");
        sb.append(String.format("合计金额：%,.2f 元\n", totalAmount));
        sb.append("报告结论：上述大额交易须在 T+5 工作日内上报中国人民银行。\n");
        sb.append("═══════════════════════════════════════════════════════════\n");

        return sb.toString();
    }

    /**
     * 生成可疑交易报告（Suspicious Activity Report）
     * 格式符合《金融机构可疑交易报告管理办法》
     *
     * @param accountId 账户ID（null 表示全量）
     * @return 格式化报告文本
     */
    public String generateSuspiciousActivityReport(String accountId) {
        log.info("生成可疑交易报告，账户：{}", accountId == null ? "全量" : accountId);

        List<Transaction> suspicious = dataLoaderService.getAllTransactions().stream()
            .filter(t -> !"normal".equalsIgnoreCase(t.getRiskFlag())
                      && !"large_normal".equalsIgnoreCase(t.getRiskFlag()))
            .filter(t -> accountId == null
                      || accountId.equalsIgnoreCase(t.getFromAccount())
                      || accountId.equalsIgnoreCase(t.getToAccount()))
            .sorted((a, b) -> b.getAmount().compareTo(a.getAmount()))
            .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append("═══════════════════════════════════════════════════════════\n");
        sb.append("                可疑交易报告（SAR）\n");
        sb.append("═══════════════════════════════════════════════════════════\n");
        sb.append(String.format("报告日期：%s\n", LocalDateTime.now().format(FMT)));
        sb.append(String.format("涉及账户：%s\n", accountId == null ? "全部账户" : accountId));
        sb.append(String.format("可疑交易类型：资金拆分(smurfing) / 分层转账(layering) / 可疑汇款(suspicious)\n"));
        sb.append("───────────────────────────────────────────────────────────\n");
        sb.append(String.format("共发现 %d 笔可疑交易\n\n", suspicious.size()));

        long smurfing  = suspicious.stream().filter(t -> "structuring".equalsIgnoreCase(t.getRiskFlag())).count();
        long layering  = suspicious.stream().filter(t -> "layering".equalsIgnoreCase(t.getRiskFlag())).count();
        long other     = suspicious.stream().filter(t -> "suspicious".equalsIgnoreCase(t.getRiskFlag())).count();

        sb.append(String.format("  - 资金拆分（Structuring）：%d 笔\n", smurfing));
        sb.append(String.format("  - 分层转账（Layering）   ：%d 笔\n", layering));
        sb.append(String.format("  - 其他可疑               ：%d 笔\n\n", other));

        for (int i = 0; i < Math.min(suspicious.size(), 15); i++) {
            Transaction t = suspicious.get(i);
            sb.append(String.format("  [%s] %s %s→%s ¥%,.0f [%s]\n",
                t.getTxnId(), t.getDatetime() != null ? t.getDatetime().format(FMT) : "N/A",
                t.getFromAccount(), t.getToAccount(),
                t.getAmount().doubleValue(), t.getRiskFlag()));
        }

        double totalSuspicious = suspicious.stream().mapToDouble(t -> t.getAmount().doubleValue()).sum();
        sb.append("───────────────────────────────────────────────────────────\n");
        sb.append(String.format("可疑交易总金额：%,.2f 元\n", totalSuspicious));
        sb.append("报告结论：上述可疑交易须在 T+10 工作日内上报，高风险账户须立即冻结调查。\n");
        sb.append("═══════════════════════════════════════════════════════════\n");

        return sb.toString();
    }

    /**
     * 生成 Claude Code 分析提示词（供演示时使用）
     * 这个方法展示了如何构建给 Claude 的 system prompt，
     * 让 Claude 知道如何调用 MCP 工具完成分析。
     *
     * @return Claude system prompt 文本
     */
    public String generateClaudePrompt() {
        return "你是一名专业的反洗钱合规分析师，配备了以下数据分析工具：\n\n" +
            "可用工具（通过 MCP 协议调用）：\n" +
            "  1. tool_query_high_risk_transactions(limit)    – 查询高风险交易\n" +
            "  2. tool_query_large_transactions(threshold)    – 查询大额交易\n" +
            "  3. tool_analyze_account_risk(accountId)        – 账户风险评估\n" +
            "  4. tool_detect_smurfing_pattern(accountId,hrs) – 拆分交易检测\n" +
            "  5. tool_detect_layering_pattern(accountId,depth)– 分层转账检测\n" +
            "  6. tool_generate_risk_summary()                – 生成风险汇总\n" +
            "  7. tool_get_regulatory_thresholds()            – 获取监管阈值\n\n" +
            "请按照以下步骤完成反洗钱分析报告：\n" +
            "  Step 1: 调用 tool_get_regulatory_thresholds() 了解监管要求\n" +
            "  Step 2: 调用 tool_generate_risk_summary() 获取整体风险概况\n" +
            "  Step 3: 调用 tool_query_high_risk_transactions(10) 识别高危案例\n" +
            "  Step 4: 对每个高危账户调用 tool_analyze_account_risk() 深入分析\n" +
            "  Step 5: 对可疑账户调用 tool_detect_smurfing_pattern() 和 tool_detect_layering_pattern()\n" +
            "  Step 6: 综合上述分析，生成符合监管要求的中文合规报告\n\n" +
            "报告格式要求：\n" +
            "  - 报告标题：反洗钱可疑交易分析报告\n" +
            "  - 一、执行摘要（含风险等级和主要发现）\n" +
            "  - 二、高风险交易详情（含交易链路分析）\n" +
            "  - 三、账户风险画像（重点账户专项分析）\n" +
            "  - 四、合规建议（分优先级排列）\n" +
            "  - 五、上报清单（需在规定期限内上报的交易）\n";
    }
}
