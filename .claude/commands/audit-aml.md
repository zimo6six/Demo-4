# Claude Code Demo 4 – AML 反洗钱智能监控

## 项目说明
本项目通过 MCP 工具让 Claude Code 自动执行反洗钱分析。

## 启动服务
```bash
mvn spring-boot:run
```
服务启动后访问：http://localhost:8082/aml/dashboard

## 使用 /audit-aml 命令

在 Claude Code 中运行：
```
/audit-aml
```

Claude 将自动：
1. 调用 `tool_get_regulatory_thresholds()` 了解监管要求
2. 调用 `tool_generate_risk_summary()` 获取风险概况
3. 调用 `tool_query_high_risk_transactions(10)` 识别高危交易
4. 对可疑账户调用 `tool_analyze_account_risk()`
5. 调用 `tool_detect_smurfing_pattern()` 和 `tool_detect_layering_pattern()`
6. 生成完整中文合规报告

## MCP 工具端点
- GET /aml/dashboard
- GET /aml/transactions/risky
- GET /aml/account/{id}/risk
- GET /aml/detect/smurfing?accountId=ACC008
- GET /aml/detect/layering?accountId=ACC010
- GET /aml/report/summary
- GET /aml/regulatory/thresholds

## 数据文件
- `data/transactions.csv` – 101 笔模拟交易
- `data/accounts.csv` – 12 个账户画像

## 注意事项
- 所有数据均为模拟生成，仅用于教学演示
- 服务默认运行在 8082 端口
- H2 控制台：http://localhost:8082/h2-console（用户名: sa，无密码）
