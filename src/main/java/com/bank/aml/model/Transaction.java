package com.bank.aml.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易记录实体
 */
public class Transaction {

    private String        txnId;
    private LocalDateTime datetime;
    private String        fromAccount;
    private String        toAccount;
    private BigDecimal    amount;
    private String        channel;      // WEB/APP/POS/ATM/API/COUNTER
    private String        remark;
    private String        txnType;      // transfer / cash_deposit / cash_withdraw
    private String        riskFlag;     // 数据标注（仅用于测试验证）

    public Transaction() {}

    // ── Getters & Setters ─────────────────────────────────────────────
    public String        getTxnId()                          { return txnId; }
    public void          setTxnId(String v)                  { this.txnId = v; }
    public LocalDateTime getDatetime()                       { return datetime; }
    public void          setDatetime(LocalDateTime v)        { this.datetime = v; }
    public String        getFromAccount()                    { return fromAccount; }
    public void          setFromAccount(String v)            { this.fromAccount = v; }
    public String        getToAccount()                      { return toAccount; }
    public void          setToAccount(String v)              { this.toAccount = v; }
    public BigDecimal    getAmount()                         { return amount; }
    public void          setAmount(BigDecimal v)             { this.amount = v; }
    public String        getChannel()                        { return channel; }
    public void          setChannel(String v)                { this.channel = v; }
    public String        getRemark()                         { return remark; }
    public void          setRemark(String v)                 { this.remark = v; }
    public String        getTxnType()                        { return txnType; }
    public void          setTxnType(String v)                { this.txnType = v; }
    public String        getRiskFlag()                       { return riskFlag; }
    public void          setRiskFlag(String v)               { this.riskFlag = v; }

    @Override
    public String toString() {
        return "Transaction{txnId='" + txnId + "', amount=" + amount + ", from=" + fromAccount + "}";
    }
}
