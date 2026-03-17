package com.bank.aml.rules;

import com.bank.aml.model.AccountProfile;
import com.bank.aml.model.RiskAssessment;
import com.bank.aml.model.Transaction;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * AML 规则引擎
 */
@Component
public class AmlRuleEngine {

    // ── 申报阈值常量 ──────────────────────────────────────────────────
    /** 现金交易大额申报线：5万元 */
    private static final BigDecimal CASH_REPORT_THRESHOLD     = new BigDecimal("50000");
    /** 转账交易大额申报线：20万元 */
    private static final BigDecimal TRANSFER_REPORT_THRESHOLD = new BigDecimal("200000");
    /** 金额拆分嗅探线：申报线的 95% */
    private static final BigDecimal SPLIT_DETECT_THRESHOLD    = new BigDecimal("47500");
    /** 凌晨时间段：22:00 - 06:00 */
    private static final LocalTime  NIGHT_START = LocalTime.of(22, 0);
    private static final LocalTime  NIGHT_END   = LocalTime.of(6, 0);

    // ═══════════════════════════════════════════════════════════════════
    // 核心评估入口
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 对单笔交易进行全面风险评估
     *
     * @param txn         被评估的交易
     * @param account     转出账户画像
     * @param recentTxns  该账户最近 24 小时的交易历史
     * @param allTxns     全量交易（用于关联分析）
     * @return            完整的风险评估结果
     */
    public RiskAssessment evaluate(Transaction txn, AccountProfile account,
                                   List<Transaction> recentTxns, List<Transaction> allTxns) {
        RiskAssessment result = new RiskAssessment(txn.getTxnId());

        // 1. 金额维度评分
        result.setAmountScore(scoreAmount(txn, result));

        // 2. 频率维度评分
        result.setFrequencyScore(scoreFrequency(txn, recentTxns, result));

        // 3. 模式匹配维度评分
        result.setPatternScore(scorePattern(txn, recentTxns, allTxns, result));

        // 4. 客户画像维度评分
        result.setProfileScore(scoreProfile(txn, account, result));

        // 5. 关联分析维度评分
        result.setRelationScore(scoreRelation(txn, allTxns, result));

        // 计算总分并确定等级
        result.calculateTotalScore();

        // 生成分析说明和建议行动
        result.setAnalysisReason(buildAnalysisReason(result, txn, account));
        result.setRecommendedAction(buildRecommendedAction(result));

        return result;
    }

    // ═══════════════════════════════════════════════════════════════════
    // 维度1：交易金额（权重 25%，满分 100）
    // ═══════════════════════════════════════════════════════════════════

    private int scoreAmount(Transaction txn, RiskAssessment result) {
        BigDecimal amount = txn.getAmount();
        int score = 0;

        // 规则：超过大额申报线
        boolean isCash = "cash_deposit".equals(txn.getTxnType())
                       || "cash_withdraw".equals(txn.getTxnType());
        BigDecimal threshold = isCash ? CASH_REPORT_THRESHOLD : TRANSFER_REPORT_THRESHOLD;

        if (amount.compareTo(threshold) >= 0) {
            score = 100;
            result.addSuspiciousType("大额交易（超申报线）");
        } else if (amount.compareTo(threshold.multiply(new BigDecimal("0.8"))) >= 0) {
            // 接近申报线（80%-100%），可能是拆分前奏
            score = 60;
            result.addSuspiciousType("接近申报阈值");
        } else if (amount.compareTo(SPLIT_DETECT_THRESHOLD) >= 0 && isCash) {
            // 现金交易接近拆分检测线
            score = 40;
        }

        return Math.min(score, 100);
    }

    // ═══════════════════════════════════════════════════════════════════
    // 维度2：交易频率（权重 20%，满分 100）
    // ═══════════════════════════════════════════════════════════════════

    private int scoreFrequency(Transaction txn, List<Transaction> recentTxns,
                                RiskAssessment result) {
        // 统计 24 小时内同方向交易笔数
        long sameDirectionCount = recentTxns.stream()
            .filter(t -> t.getFromAccount().equals(txn.getFromAccount()))
            .count();

        if (sameDirectionCount > 10) {
            result.addSuspiciousType("频率异常（24h内同向>" + sameDirectionCount + "笔）");
            return 100;
        } else if (sameDirectionCount > 5) {
            result.addSuspiciousType("频率偏高（24h内同向>" + sameDirectionCount + "笔）");
            return 60;
        } else if (sameDirectionCount > 3) {
            return 30;
        }
        return 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    // 维度3：模式匹配（权重 25%，满分 100）
    // ═══════════════════════════════════════════════════════════════════

    private int scorePattern(Transaction txn, List<Transaction> recentTxns,
                              List<Transaction> allTxns, RiskAssessment result) {
        int score = 0;

        // 模式1：金额拆分检测（多笔接近但不超申报线）
        score = Math.max(score, detectAmountSplit(txn, recentTxns, result));

        // 模式2：快进快出（资金过道）
        score = Math.max(score, detectRapidPassthrough(txn, allTxns, result));

        // 模式3：凌晨时间异常
        score = Math.max(score, detectMidnightActivity(txn, result));

        // 模式4：分散转入集中转出
        score = Math.max(score, detectScatterConcentrate(txn, allTxns, result));

        return Math.min(score, 100);
    }

    /** 模式1：金额拆分 */
    private int detectAmountSplit(Transaction txn, List<Transaction> recentTxns,
                                   RiskAssessment result) {
        // 检测：同一账户 24 小时内，多笔金额在 4.5万~5万之间
        long splitCount = recentTxns.stream()
            .filter(t -> t.getFromAccount().equals(txn.getFromAccount()))
            .filter(t -> {
                BigDecimal amt = t.getAmount();
                return amt.compareTo(new BigDecimal("45000")) >= 0
                    && amt.compareTo(CASH_REPORT_THRESHOLD)   <  0;
            })
            .count();

        if (splitCount >= 3) {
            result.addSuspiciousType("金额拆分（" + splitCount + "笔接近申报线）");
            return 90;
        } else if (splitCount >= 2) {
            result.addSuspiciousType("疑似金额拆分");
            return 60;
        }
        return 0;
    }

    /** 模式2：快进快出 */
    private int detectRapidPassthrough(Transaction txn, List<Transaction> allTxns,
                                        RiskAssessment result) {
        // 检测：账户在 6 小时内既有大额收入又有大额支出
        String account = txn.getToAccount();
        BigDecimal largeThreshold = new BigDecimal("50000");

        // 该账户的大额转入
        List<Transaction> largeInflow = allTxns.stream()
            .filter(t -> t.getToAccount().equals(account))
            .filter(t -> t.getAmount().compareTo(largeThreshold) >= 0)
            .collect(Collectors.toList());

        // 该账户的大额转出
        List<Transaction> largeOutflow = allTxns.stream()
            .filter(t -> t.getFromAccount().equals(account))
            .filter(t -> t.getAmount().compareTo(largeThreshold) >= 0)
            .collect(Collectors.toList());

        if (!largeInflow.isEmpty() && !largeOutflow.isEmpty()) {
            // 简化判断：既有大额入又有大额出，且出流分散（>2个不同目标账户）
            long distinctTargets = largeOutflow.stream()
                .map(Transaction::getToAccount).distinct().count();
            if (distinctTargets >= 3) {
                result.addSuspiciousType("快进快出（资金通道账户，" + distinctTargets + "个转出目标）");
                return 95;
            } else if (distinctTargets >= 2) {
                result.addSuspiciousType("疑似快进快出");
                return 70;
            }
        }
        return 0;
    }

    /** 模式3：凌晨时间异常 */
    private int detectMidnightActivity(Transaction txn, RiskAssessment result) {
        if (txn.getDatetime() == null) return 0;
        LocalTime txnTime = txn.getDatetime().toLocalTime();

        boolean isMidnight = txnTime.isAfter(NIGHT_START) || txnTime.isBefore(NIGHT_END);
        if (isMidnight && txn.getAmount().compareTo(new BigDecimal("20000")) >= 0) {
            result.addSuspiciousType("时间异常（凌晨大额交易 " + txnTime + "）");
            return 70;
        }
        return 0;
    }

    /** 模式4：分散转入集中转出 */
    private int detectScatterConcentrate(Transaction txn, List<Transaction> allTxns,
                                          RiskAssessment result) {
        // 检测转入端：多个来源 → 单个账户
        String toAccount = txn.getToAccount();

        long distinctSources = allTxns.stream()
            .filter(t -> t.getToAccount().equals(toAccount))
            .map(Transaction::getFromAccount).distinct().count();

        // 该账户转出笔数明显少于转入笔数（集中转出特征）
        long outCount = allTxns.stream()
            .filter(t -> t.getFromAccount().equals(toAccount)).count();
        long inCount  = allTxns.stream()
            .filter(t -> t.getToAccount().equals(toAccount)).count();

        if (distinctSources >= 5 && outCount <= 2 && inCount >= 5) {
            result.addSuspiciousType("分散转入集中转出（" + distinctSources + "个来源→" + outCount + "笔转出）");
            return 85;
        }
        return 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    // 维度4：客户画像（权重 15%，满分 100）
    // ═══════════════════════════════════════════════════════════════════

    private int scoreProfile(Transaction txn, AccountProfile account,
                              RiskAssessment result) {
        if (account == null) return 50; // 画像缺失，中等风险

        int score = 0;

        // 高风险行业
        if ("shell".equals(account.getIndustry())) {
            score += 60;
            result.addSuspiciousType("账户特征：空壳公司");
        } else if ("unemployed".equals(account.getIndustry())) {
            // 无业人员但有大额交易
            if (txn.getAmount().compareTo(new BigDecimal("20000")) > 0) {
                score += 40;
                result.addSuspiciousType("客户画像异常（无业人员大额转账）");
            }
        }

        // 账户固有风险等级
        if ("high".equals(account.getRiskLevel()))        score += 30;
        else if ("medium".equals(account.getRiskLevel())) score += 15;

        // 交易金额与收入水平明显不符
        if (txn.getAmount().doubleValue() > account.getBalance() * 5) {
            score += 20;
            result.addSuspiciousType("金额与资产不符（交易额远超账户余额）");
        }

        return Math.min(score, 100);
    }

    // ═══════════════════════════════════════════════════════════════════
    // 维度5：关联分析（权重 15%，满分 100）
    // ═══════════════════════════════════════════════════════════════════

    private int scoreRelation(Transaction txn, List<Transaction> allTxns,
                               RiskAssessment result) {
        // 循环转账检测：A→B→C→A 链路
        String from = txn.getFromAccount();
        String to   = txn.getToAccount();

        // 检测：to 账户是否曾向 from 账户转账（直接环路）
        boolean directCycle = allTxns.stream()
            .anyMatch(t -> t.getFromAccount().equals(to)
                        && t.getToAccount().equals(from));

        if (directCycle) {
            result.addSuspiciousType("关联循环转账（直接环路 " + from + "↔" + to + "）");
            return 90;
        }

        // 检测：三跳环路（A→B→C→A）
        boolean threeHopCycle = allTxns.stream()
            .filter(t -> t.getFromAccount().equals(to))
            .anyMatch(t2 -> allTxns.stream()
                .anyMatch(t3 -> t3.getFromAccount().equals(t2.getToAccount())
                              && t3.getToAccount().equals(from)));

        if (threeHopCycle) {
            result.addSuspiciousType("关联循环转账（三跳环路）");
            return 70;
        }

        return 0;
    }

    // ═══════════════════════════════════════════════════════════════════
    // 报告生成
    // ═══════════════════════════════════════════════════════════════════

    private String buildAnalysisReason(RiskAssessment r, Transaction txn, AccountProfile acc) {
        StringBuilder sb = new StringBuilder();
        sb.append("交易 ").append(txn.getTxnId())
          .append(" 风险评分 ").append(r.getTotalScore()).append("分。");

        if (!r.getSuspiciousTypes().isEmpty()) {
            sb.append("命中风险模式：").append(String.join("；", r.getSuspiciousTypes())).append("。");
        }
        sb.append("各维度得分——金额:").append(r.getAmountScore())
          .append(" 频率:").append(r.getFrequencyScore())
          .append(" 模式:").append(r.getPatternScore())
          .append(" 画像:").append(r.getProfileScore())
          .append(" 关联:").append(r.getRelationScore()).append("。");

        if (acc != null) {
            sb.append("账户 ").append(acc.getName()).append("（").append(acc.getType())
              .append("/").append(acc.getRiskLevel()).append("风险）。");
        }
        return sb.toString();
    }

    private String buildRecommendedAction(RiskAssessment r) {
        switch (r.getRiskLevel()) {
            case "HIGH":   return "立即人工审核 + 提交可疑交易报告（SAR）至人民银行反洗钱中心";
            case "MEDIUM": return "建议人工复核，关注后续交易行为，必要时冻结账户";
            default:       return "自动归档，纳入持续监控名单，保留记录 5 年";
        }
    }
}
