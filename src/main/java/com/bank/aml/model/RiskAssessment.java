package com.bank.aml.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 风险评估结果
 */
public class RiskAssessment {

    /** 关联的交易 ID */
    private String txnId;

    /** 总风险评分（0-100） */
    private int totalScore;

    /** 风险等级：HIGH(≥80) / MEDIUM(60-79) / LOW(<60) */
    private String riskLevel;

    /** 触发的可疑类型列表 */
    private List<String> suspiciousTypes = new ArrayList<>();

    /** 各维度评分明细 */
    private int amountScore;      // 交易金额维度（权重25%）
    private int frequencyScore;   // 交易频率维度（权重20%）
    private int patternScore;     // 模式匹配维度（权重25%）
    private int profileScore;     // 客户画像维度（权重15%）
    private int relationScore;    // 关联分析维度（权重15%）

    /** AI 分析说明 */
    private String analysisReason;

    /** 建议行动 */
    private String recommendedAction;

    /** 评估时间 */
    private LocalDateTime assessedAt;

    // ── 账户级评估字段（assessAccountRisk 使用）────────────────────────
    /** 账户 ID */
    private String accountId;
    /** 风险评分（浮点，0-100） */
    private double riskScore;
    /** 总交易笔数 */
    private int totalTxnCount;
    /** 可疑交易笔数 */
    private int suspiciousTxnCount;
    /** 大额交易笔数 */
    private int largeAmountCount;
    /** 总交易金额 */
    private BigDecimal totalAmount = BigDecimal.ZERO;
    /** 主要风险因素描述列表 */
    private List<String> mainRiskFactors = new ArrayList<>();
    /** 处理建议 */
    private String suggestion;

    // ── 构造器 ──────────────────────────────────────────────────────
    public RiskAssessment() {
        this.assessedAt = LocalDateTime.now();
    }

    public RiskAssessment(String txnId) {
        this.txnId      = txnId;
        this.assessedAt = LocalDateTime.now();
    }

    // ── 派生计算 ─────────────────────────────────────────────────────
    /** 根据各维度得分计算加权总分 */
    public void calculateTotalScore() {
        this.totalScore = (int)(
            amountScore    * 0.25 +
            frequencyScore * 0.20 +
            patternScore   * 0.25 +
            profileScore   * 0.15 +
            relationScore  * 0.15
        );
        // 确定风险等级
        if (totalScore >= 80)      this.riskLevel = "HIGH";
        else if (totalScore >= 60) this.riskLevel = "MEDIUM";
        else                       this.riskLevel = "LOW";
    }

    // ── Getters & Setters ─────────────────────────────────────────────
    public String          getTxnId()                        { return txnId; }
    public void            setTxnId(String v)                { this.txnId = v; }
    public int             getTotalScore()                   { return totalScore; }
    public void            setTotalScore(int v)              { this.totalScore = v; }
    public String          getRiskLevel()                    { return riskLevel; }
    public void            setRiskLevel(String v)            { this.riskLevel = v; }
    public List<String>    getSuspiciousTypes()              { return suspiciousTypes; }
    public void            setSuspiciousTypes(List<String> v){ this.suspiciousTypes = v; }
    public void            addSuspiciousType(String t)       { this.suspiciousTypes.add(t); }
    public int             getAmountScore()                  { return amountScore; }
    public void            setAmountScore(int v)             { this.amountScore = v; }
    public int             getFrequencyScore()               { return frequencyScore; }
    public void            setFrequencyScore(int v)          { this.frequencyScore = v; }
    public int             getPatternScore()                 { return patternScore; }
    public void            setPatternScore(int v)            { this.patternScore = v; }
    public int             getProfileScore()                 { return profileScore; }
    public void            setProfileScore(int v)            { this.profileScore = v; }
    public int             getRelationScore()                { return relationScore; }
    public void            setRelationScore(int v)           { this.relationScore = v; }
    public String          getAnalysisReason()               { return analysisReason; }
    public void            setAnalysisReason(String v)       { this.analysisReason = v; }
    public String          getRecommendedAction()            { return recommendedAction; }
    public void            setRecommendedAction(String v)    { this.recommendedAction = v; }
    public LocalDateTime   getAssessedAt()                   { return assessedAt; }
    public void            setAssessedAt(LocalDateTime v)    { this.assessedAt = v; }
    public String          getAccountId()                    { return accountId; }
    public void            setAccountId(String v)            { this.accountId = v; }
    public double          getRiskScore()                    { return riskScore; }
    public void            setRiskScore(double v)            { this.riskScore = v; }
    public int             getTotalTxnCount()                { return totalTxnCount; }
    public void            setTotalTxnCount(int v)           { this.totalTxnCount = v; }
    public int             getSuspiciousTxnCount()           { return suspiciousTxnCount; }
    public void            setSuspiciousTxnCount(int v)      { this.suspiciousTxnCount = v; }
    public int             getLargeAmountCount()             { return largeAmountCount; }
    public void            setLargeAmountCount(int v)        { this.largeAmountCount = v; }
    public BigDecimal      getTotalAmount()                  { return totalAmount; }
    public void            setTotalAmount(BigDecimal v)      { this.totalAmount = v; }
    public List<String>    getMainRiskFactors()              { return mainRiskFactors; }
    public void            setMainRiskFactors(List<String> v){ this.mainRiskFactors = v; }
    public String          getSuggestion()                   { return suggestion; }
    public void            setSuggestion(String v)           { this.suggestion = v; }

    @Override
    public String toString() {
        return "RiskAssessment{txnId='" + txnId + "', score=" + totalScore
                + ", level='" + riskLevel + "', types=" + suspiciousTypes + "}";
    }
}
