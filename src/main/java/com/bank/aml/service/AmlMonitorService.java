package com.bank.aml.service;

import com.bank.aml.model.AccountProfile;
import com.bank.aml.model.RiskAssessment;
import com.bank.aml.model.Transaction;
import com.bank.aml.rules.AmlRuleEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Demo 4 – AML 反洗钱监控核心服务
 *
 * 功能：
 *   1. 批量扫描交易数据
 *   2. 调用规则引擎对每笔交易进行风险评估
 *   3. 按风险等级分组输出结果
 *   4. 生成汇总统计
 */
@Service
public class AmlMonitorService {

    private static final Logger log = LoggerFactory.getLogger(AmlMonitorService.class);

    @Autowired
    private AmlRuleEngine ruleEngine;

    @Autowired
    private DataLoaderService dataLoaderService;

    // ── 核心方法 ─────────────────────────────────────────────────────

    /**
     * 批量扫描交易列表，返回所有中高风险评估结果
     *
     * @param transactions  待扫描的交易列表
     * @param accountMap    账户画像字典（accountId → AccountProfile）
     * @return 风险评估结果列表（按评分降序）
     */
    public List<RiskAssessment> scanTransactions(List<Transaction> transactions,
                                                  Map<String, AccountProfile> accountMap) {
        log.info("开始 AML 扫描，共 {} 笔交易", transactions.size());
        List<RiskAssessment> results = new ArrayList<>();

        for (Transaction txn : transactions) {
            try {
                AccountProfile account = accountMap.get(txn.getFromAccount());

                // 获取该账户最近 24 小时的交易（简化：取时间在此笔之前 24h 内的记录）
                List<Transaction> recentTxns = getRecentTransactions(txn, transactions, 24);

                RiskAssessment assessment = ruleEngine.evaluate(txn, account, recentTxns, transactions);

                // 只返回中高风险结果（低风险自动归档）
                if (!"LOW".equals(assessment.getRiskLevel())) {
                    results.add(assessment);
                    log.info("发现风险交易 txnId={} score={} level={}",
                            txn.getTxnId(), assessment.getTotalScore(), assessment.getRiskLevel());
                }

            } catch (Exception e) {
                log.error("评估交易失败 txnId={}", txn.getTxnId(), e);
            }
        }

        // 按评分降序排列
        results.sort((a, b) -> Integer.compare(b.getTotalScore(), a.getTotalScore()));
        log.info("AML 扫描完成，发现 {} 笔中高风险交易", results.size());
        return results;
    }

    /**
     * 对单笔交易进行实时风险评估（接入支付流水时使用）
     */
    public RiskAssessment evaluateSingle(Transaction txn, Map<String, AccountProfile> accountMap,
                                          List<Transaction> historyTxns) {
        AccountProfile account = accountMap.get(txn.getFromAccount());
        List<Transaction> recentTxns = getRecentTransactions(txn, historyTxns, 24);
        return ruleEngine.evaluate(txn, account, recentTxns, historyTxns);
    }

    /**
     * 生成扫描汇总统计
     */
    public Map<String, Object> buildSummary(List<Transaction> allTxns,
                                             List<RiskAssessment> riskResults) {
        Map<String, Object> summary = new LinkedHashMap<>();

        long highCount   = riskResults.stream().filter(r -> "HIGH".equals(r.getRiskLevel())).count();
        long mediumCount = riskResults.stream().filter(r -> "MEDIUM".equals(r.getRiskLevel())).count();
        long lowCount    = allTxns.size() - highCount - mediumCount;

        // 涉嫌洗钱的总金额
        BigDecimal suspiciousAmount = riskResults.stream()
            .map(r -> {
                Transaction t = allTxns.stream()
                    .filter(tx -> tx.getTxnId().equals(r.getTxnId()))
                    .findFirst().orElse(null);
                return t != null ? t.getAmount() : BigDecimal.ZERO;
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 最高风险账户
        Map<String, Long> accountRiskCount = riskResults.stream()
            .collect(Collectors.groupingBy(
                r -> {
                    Transaction t = allTxns.stream()
                        .filter(tx -> tx.getTxnId().equals(r.getTxnId()))
                        .findFirst().map(Transaction::getFromAccount).orElse("unknown");
                    return t;
                },
                Collectors.counting()
            ));
        String topRiskAccount = accountRiskCount.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey).orElse("N/A");

        // 最常见可疑类型
        Map<String, Long> typeCount = new LinkedHashMap<>();
        riskResults.forEach(r -> r.getSuspiciousTypes()
            .forEach(t -> typeCount.merge(t, 1L, Long::sum)));
        String topSuspiciousType = typeCount.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey).orElse("N/A");

        summary.put("scanTime",         LocalDateTime.now().toString());
        summary.put("totalTxns",         allTxns.size());
        summary.put("highRiskCount",     highCount);
        summary.put("mediumRiskCount",   mediumCount);
        summary.put("lowRiskCount",      lowCount);
        summary.put("suspiciousAmountTotal", suspiciousAmount);
        summary.put("topRiskAccount",    topRiskAccount);
        summary.put("topSuspiciousType", topSuspiciousType);
        summary.put("sarRequiredCount",  highCount);  // 高风险需提交 SAR

        return summary;
    }

    // ── 内部工具 ─────────────────────────────────────────────────────

    /**
     * 对指定账户进行风险评估（MCP 工具调用）
     *
     * @param accountId 账户ID
     * @return 风险评估结果，若账户不存在返回 null
     */
    public RiskAssessment assessAccountRisk(String accountId) {
        List<Transaction> accountTxns = dataLoaderService.getTransactionsByAccount(accountId);
        if (accountTxns.isEmpty()) {
            AccountProfile profile = dataLoaderService.getAccountMap().get(accountId);
            if (profile == null) return null;
        }

        RiskAssessment assessment = new RiskAssessment();
        assessment.setAccountId(accountId);
        assessment.setTxnId("ACCOUNT_ASSESSMENT_" + accountId);

        long suspiciousCount = accountTxns.stream()
            .filter(t -> !"normal".equalsIgnoreCase(t.getRiskFlag())
                      && !"large_normal".equalsIgnoreCase(t.getRiskFlag()))
            .count();

        long largeCount = accountTxns.stream()
            .filter(t -> t.getAmount().compareTo(BigDecimal.valueOf(50000)) >= 0)
            .count();

        BigDecimal total = accountTxns.stream()
            .filter(t -> accountId.equalsIgnoreCase(t.getFromAccount()))
            .map(Transaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        double score = 0;
        if (suspiciousCount >= 5) score += 40;
        else if (suspiciousCount >= 2) score += 20;
        if (largeCount >= 3) score += 30;
        else if (largeCount >= 1) score += 15;
        if (accountTxns.size() > 20) score += 10;

        AccountProfile profile = dataLoaderService.getAccountMap().get(accountId);
        if (profile != null && "high".equalsIgnoreCase(profile.getRiskLevel())) {
            score += 20;
        }

        assessment.setRiskScore(score);
        assessment.setRiskLevel(score >= 60 ? "HIGH" : score >= 30 ? "MEDIUM" : "LOW");
        assessment.setTotalTxnCount((int) accountTxns.size());
        assessment.setSuspiciousTxnCount((int) suspiciousCount);
        assessment.setLargeAmountCount((int) largeCount);
        assessment.setTotalAmount(total);

        List<String> factors = new ArrayList<>();
        if (suspiciousCount > 0) factors.add("存在" + suspiciousCount + "笔可疑交易");
        if (largeCount > 0) factors.add("存在" + largeCount + "笔大额交易");
        if (profile != null && "high".equalsIgnoreCase(profile.getRiskLevel())) factors.add("账户本身为高风险客户");
        assessment.setMainRiskFactors(factors);
        assessment.setSuggestion(score >= 60
            ? "建议立即上报监管机构并暂停账户"
            : score >= 30 ? "建议持续监控，必要时提交可疑交易报告" : "正常账户，继续例行监控");

        return assessment;
    }

    /**
     * 追踪分层转账链路（MCP 工具调用）
     *
     * @param startAccountId 起始账户
     * @param maxDepth       最大追踪深度
     * @return 分层链路上的交易列表
     */
    public List<Transaction> traceLayeringChain(String startAccountId, int maxDepth) {
        List<Transaction> all = dataLoaderService.getAllTransactions();
        List<Transaction> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        visited.add(startAccountId);

        String currentAccount = startAccountId;
        for (int depth = 0; depth < maxDepth; depth++) {
            final String curr = currentAccount;
            // 找当前账户发出的最近一笔"layering"交易
            Optional<Transaction> nextTxn = all.stream()
                .filter(t -> curr.equalsIgnoreCase(t.getFromAccount()))
                .filter(t -> "layering".equalsIgnoreCase(t.getRiskFlag())
                          || "suspicious".equalsIgnoreCase(t.getRiskFlag()))
                .filter(t -> !visited.contains(t.getToAccount()))
                .findFirst();

            if (!nextTxn.isPresent()) break;

            Transaction txn = nextTxn.get();
            chain.add(txn);
            visited.add(txn.getToAccount());
            currentAccount = txn.getToAccount();
        }

        return chain;
    }

    // ── 内部工具 ─────────────────────────────────────────────────────

    private List<Transaction> getRecentTransactions(Transaction pivot,
                                                     List<Transaction> all, int hours) {
        if (pivot.getDatetime() == null) return Collections.emptyList();
        LocalDateTime cutoff = pivot.getDatetime().minusHours(hours);
        return all.stream()
            .filter(t -> t != pivot)
            .filter(t -> t.getDatetime() != null && t.getDatetime().isAfter(cutoff)
                      && t.getDatetime().isBefore(pivot.getDatetime()))
            .collect(Collectors.toList());
    }
}
