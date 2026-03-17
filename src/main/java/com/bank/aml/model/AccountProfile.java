package com.bank.aml.model;

/**
 * 账户实体
 */
public class AccountProfile {

    private String id;
    private String name;
    private String type;       // personal / company
    private String riskLevel;  // low / medium / high
    private double balance;
    private String industry;   // employee / trade / finance / shell / real_estate / unemployed

    public AccountProfile() {}
    public AccountProfile(String id, String name, String type, String riskLevel,
                          double balance, String industry) {
        this.id        = id;
        this.name      = name;
        this.type      = type;
        this.riskLevel = riskLevel;
        this.balance   = balance;
        this.industry  = industry;
    }

    public String getId()            { return id; }
    public void   setId(String v)    { this.id = v; }
    public String getName()          { return name; }
    public void   setName(String v)  { this.name = v; }
    public String getType()          { return type; }
    public void   setType(String v)  { this.type = v; }
    public String getRiskLevel()     { return riskLevel; }
    public void   setRiskLevel(String v) { this.riskLevel = v; }
    public double getBalance()       { return balance; }
    public void   setBalance(double v)   { this.balance = v; }
    public String getIndustry()      { return industry; }
    public void   setIndustry(String v)  { this.industry = v; }

    @Override
    public String toString() {
        return "AccountProfile{id='" + id + "', name='" + name + "', risk='" + riskLevel + "'}";
    }
}
