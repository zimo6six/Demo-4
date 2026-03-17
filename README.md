# Demo 4 – AML Monitor (Claude Code MCP Demo)

## 项目概述

这是 **Demo 4** 的演示代码仓库，展示 Claude Code 通过 **MCP（Model Context Protocol）** 工具  
自动完成**反洗钱（AML）智能监控分析**的完整工作流。

### 演示目标
- Claude Code 通过 MCP 工具调用后端服务接口
- 自动分析可疑交易模式（拆分/分层/循环）  
- 生成符合监管要求的中文合规报告

---

## 快速开始

```bash
# 1. 克隆仓库
git clone https://github.com/zimo6six/Demo-4.git
cd Demo-4

# 2. 编译运行
mvn spring-boot:run

# 3. 验证服务
curl http://localhost:8082/aml/dashboard

# 4. 配置 Claude Code MCP（见下方说明）
```

---

## 仓库结构

```
Demo-4/
├── data/
│   ├── transactions.csv        ← 101 笔模拟交易（含8种风险模式）
│   └── accounts.csv            ← 12 个账户画像（含高/中/低风险）
├── src/main/java/com/bank/aml/
│   ├── AmlApplication.java     ← Spring Boot 启动类
│   ├── controller/
│   │   └── AmlController.java  ← REST API（8个端点）
│   ├── mcp/
│   │   └── AmlMcpTools.java    ← MCP 工具集（7个工具方法）
│   ├── model/
│   │   ├── Transaction.java    ← 交易记录实体
│   │   ├── RiskAssessment.java ← 风险评估结果
│   │   └── AccountProfile.java ← 账户画像
│   ├── rules/
│   │   └── AmlRuleEngine.java  ← 规则引擎（拆分/分层/大额检测）
│   ├── report/
│   │   └── AmlReportGenerator.java ← 合规报告生成器
│   └── service/
│       ├── AmlMonitorService.java  ← AML 核心服务
│       └── DataLoaderService.java  ← CSV 数据加载器
├── src/main/resources/
│   └── application.yml         ← 配置（端口8082，H2内存库）
├── src/test/
│   └── AmlMonitorServiceTest.java
├── pom.xml
└── README.md
```

---

## 模拟数据说明

### transactions.csv（101 笔交易）

| 风险类型 | 数量 | 说明 |
|----------|------|------|
| `normal` | 60 | 正常日常转账 |
| `suspicious` | 21 | 可疑交易（无合理说明的大额转入/转出） |
| `structuring` | 12 | 拆分交易（多笔 4000-9999 元规避报告阈值） |
| `layering` | 8 | 分层转账（A→B→C→D 多跳快速流转） |
| `large_normal` | 8 | 大额正常交易（有合理业务背景） |

### accounts.csv（12 个账户）

| 账户ID | 姓名 | 类型 | 风险级别 |
|--------|------|------|----------|
| ACC001 | 张伟 | personal | low |
| ACC003 | 王磊 | company | medium |
| ACC008 | 玄武投资 | company | **high** |
| ACC010 | 离岸贸易公司 | company | **high** |
| ... | ... | ... | ... |

---

## MCP 工具列表

| 工具名 | 功能 | 参数 |
|--------|------|------|
| `tool_query_high_risk_transactions` | 查询高风险交易 | `limit: int` |
| `tool_query_large_transactions` | 查询大额交易 | `threshold: double` |
| `tool_query_transactions_by_account` | 账户交易查询 | `accountId: String` |
| `tool_analyze_account_risk` | 账户风险评估 | `accountId: String` |
| `tool_detect_smurfing_pattern` | 拆分交易检测 | `accountId, hours` |
| `tool_detect_layering_pattern` | 分层转账检测 | `accountId, depth` |
| `tool_generate_risk_summary` | 风险汇总统计 | 无 |
| `tool_get_regulatory_thresholds` | 监管阈值查询 | 无 |

---

## REST API 端点

```
GET  /aml/dashboard                         监控仪表盘
GET  /aml/transactions?page=0&size=20       交易列表
GET  /aml/transactions/risky?limit=20       高风险交易
GET  /aml/transactions/large?threshold=50000 大额交易
GET  /aml/account/{id}/transactions         账户交易
GET  /aml/account/{id}/risk                 账户风险分析
GET  /aml/detect/smurfing?accountId=xxx     拆分检测
GET  /aml/detect/layering?accountId=xxx     分层检测
GET  /aml/report/summary                    风险汇总
GET  /aml/regulatory/thresholds             监管阈值
POST /aml/reload                            重载数据
```

---

## Claude Code 演示步骤

1. **启动服务**：`mvn spring-boot:run`
2. **配置 MCP**（在 `.claude/commands/audit-aml.md` 中）
3. **运行命令**：在 Claude Code 中执行 `/audit-aml`
4. **观察过程**：Claude 自动调用工具 → 分析数据 → 生成报告

### 预期输出（Claude 生成的报告）
```
# 反洗钱可疑交易分析报告

## 一、执行摘要
本次扫描发现 41 笔高风险交易，涉及金额 XXX 万元...

## 二、高风险交易详情
### 2.1 资金拆分（Structuring）
ACC008 在 2026-02-14 至 2026-02-16 期间...

## 三、账户风险画像
...
```

---

## 监管参考

- **大额交易**：个人单笔 ≥5万元，须在 **T+5工作日** 内上报人民银行
- **可疑交易**：行为异常或无合理理由，须在 **T+10工作日** 内上报
- **现金交易**：单笔 ≥1万元，须在 **T+3工作日** 内上报
- 法规依据：《反洗钱法》《金融机构可疑交易报告管理办法》

---

> **⚠️ 免责声明**：本项目为 Claude Code 教学演示，所有数据均为模拟生成，不代表真实业务。
