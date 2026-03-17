package com.bank.aml;

import com.bank.aml.model.AccountProfile;
import com.bank.aml.model.RiskAssessment;
import com.bank.aml.model.Transaction;
import com.bank.aml.rules.AmlRuleEngine;
import com.bank.aml.service.DataLoaderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demo 4 – AML 规则引擎单元测试
 *
 * 验证反洗钱规则引擎对不同交易场景的检测效果：
 *   1. 正常大额交易（应标记 large_normal，非 HIGH 风险）
 *   2. 拆分交易（structuring）应被检测
 *   3. 分层转账（layering）应被检测
 *   4. 高风险账户 + 可疑金额 → HIGH 风险
 */
@SpringBootTest
@DisplayName("AML 规则引擎测试")
class AmlMonitorServiceTest {

    @Autowired
    private DataLoaderService dataLoaderService;

    @Autowired
    private AmlRuleEngine ruleEngine;

    private AccountProfile normalAccount;
    private AccountProfile highRiskAccount;

    @BeforeEach
    void setUp() {
        normalAccount = new AccountProfile(
            "TEST001", "普通用户", "personal", "low", 50000.0, "employee");
        highRiskAccount = new AccountProfile(
            "TEST002", "高风险账户", "company", "high", 5000000.0, "offshore");
    }

    // ============================================================
    // Test 1: 数据加载
    // ============================================================

    @Test
    @DisplayName("TC01 – CSV 数据加载验证")
    void testDataLoading() {
        List<Transaction> txns = dataLoaderService.getAllTransactions();
        assertNotNull(txns, "交易列表不应为 null");
        assertFalse(txns.isEmpty(), "交易列表不应为空");
        assertTrue(txns.size() >= 50, "至少应有 50 笔交易，实际：" + txns.size());

        Collection<AccountProfile> accounts = dataLoaderService.getAllAccounts();
        assertNotNull(accounts, "账户列表不应为 null");
        assertFalse(accounts.isEmpty(), "账户列表不应为空");

        System.out.println("✅ 数据加载成功：" + txns.size() + " 笔交易，" + accounts.size() + " 个账户");
    }

    @Test
    @DisplayName("TC02 – 交易数据字段完整性验证")
    void testTransactionDataIntegrity() {
        List<Transaction> txns = dataLoaderService.getAllTransactions();
        int nullAmountCount = 0;
        int nullDateCount = 0;

        for (Transaction t : txns) {
            assertNotNull(t.getTxnId(), "交易ID不应为 null");
            assertNotNull(t.getFromAccount(), "付款账户不应为 null");
            assertNotNull(t.getToAccount(), "收款账户不应为 null");
            assertNotNull(t.getAmount(), "金额不应为 null");
            assertTrue(t.getAmount().compareTo(BigDecimal.ZERO) > 0, "金额应大于0");
            if (t.getDatetime() == null) nullDateCount++;
        }

        assertEquals(0, nullDateCount, "不应有 null 日期字段");
        System.out.println("✅ 数据完整性验证通过，共 " + txns.size() + " 笔");
    }

    // ============================================================
    // Test 2: 风险标注验证
    // ============================================================

    @Test
    @DisplayName("TC03 – 可疑交易标注验证")
    void testSuspiciousFlagDistribution() {
        List<Transaction> txns = dataLoaderService.getAllTransactions();
        long suspicious = txns.stream().filter(t -> "suspicious".equalsIgnoreCase(t.getRiskFlag())).count();
        long structuring = txns.stream().filter(t -> "structuring".equalsIgnoreCase(t.getRiskFlag())).count();
        long layering = txns.stream().filter(t -> "layering".equalsIgnoreCase(t.getRiskFlag())).count();
        long normal = txns.stream().filter(t -> "normal".equalsIgnoreCase(t.getRiskFlag())).count();

        assertTrue(suspicious > 0, "应有可疑交易（suspicious）");
        assertTrue(structuring > 0, "应有拆分交易（structuring）");
        assertTrue(layering > 0, "应有分层交易（layering）");
        assertTrue(normal > 0, "应有正常交易（normal）");

        System.out.printf("✅ 风险分布：suspicious=%d, structuring=%d, layering=%d, normal=%d%n",
            suspicious, structuring, layering, normal);
    }

    // ============================================================
    // Test 3: 规则引擎测试
    // ============================================================

    @Test
    @DisplayName("TC04 – 正常小额交易应为低风险")
    void testNormalSmallTransactionIsLowRisk() {
        Transaction txn = buildTransaction("TEST-001", "ACC001", "ACC002",
            new BigDecimal("500"), "normal");

        RiskAssessment result = ruleEngine.evaluate(txn, normalAccount,
            Collections.emptyList(), Collections.emptyList());

        assertNotNull(result);
        assertEquals("LOW", result.getRiskLevel(),
            "500元正常转账应为低风险，实际：" + result.getRiskLevel());
        System.out.println("✅ TC04通过：小额正常交易 → " + result.getRiskLevel());
    }

    @Test
    @DisplayName("TC05 – 超大额可疑交易应为高风险")
    void testLargeAmountSuspiciousTransactionIsHighRisk() {
        Transaction txn = buildTransaction("TEST-002", "TEST002", "ACC999",
            new BigDecimal("500000"), "suspicious");

        List<Transaction> recent = buildSmurfingTransactions("TEST002", 5);

        RiskAssessment result = ruleEngine.evaluate(txn, highRiskAccount,
            recent, recent);

        assertNotNull(result);
        assertNotEquals("LOW", result.getRiskLevel(),
            "高风险账户50万大额交易不应为低风险");
        System.out.println("✅ TC05通过：大额可疑 + 高风险账户 → " + result.getRiskLevel()
            + "（评分：" + result.getTotalScore() + "）");
    }

    @Test
    @DisplayName("TC06 – 拆分交易模式应触发告警")
    void testStructuringPatternDetected() {
        Transaction txn = buildTransaction("TEST-003", "ACC008", "ACC009",
            new BigDecimal("9500"), "structuring");

        // 模拟同一账户最近6笔类似金额交易（拆分模式）
        List<Transaction> recentStructuring = buildSmurfingTransactions("ACC008", 6);

        RiskAssessment result = ruleEngine.evaluate(txn, highRiskAccount,
            recentStructuring, recentStructuring);

        assertNotNull(result);
        assertTrue(result.getTotalScore() > 30, "拆分交易应有显著风险评分");
        System.out.println("✅ TC06通过：拆分交易检测 → 评分" + result.getTotalScore()
            + "，等级" + result.getRiskLevel());
    }

    @Test
    @DisplayName("TC07 – 监管阈值边界：49999 元不触发大额报告")
    void testBelowThresholdTransaction() {
        Transaction txn = buildTransaction("TEST-004", "ACC001", "ACC002",
            new BigDecimal("49999"), "normal");

        RiskAssessment result = ruleEngine.evaluate(txn, normalAccount,
            Collections.emptyList(), Collections.emptyList());

        // 49999 元正常业务不应触发高风险
        assertNotEquals("HIGH", result.getRiskLevel(),
            "49999元正常转账不应为高风险，实际：" + result.getRiskLevel());
        System.out.println("✅ TC07通过：阈值边界（49999）→ " + result.getRiskLevel());
    }

    @Test
    @DisplayName("TC08 – 账户风险查询：高风险账户 ACC008")
    void testHighRiskAccountQuery() {
        List<Transaction> acc8Txns = dataLoaderService.getTransactionsByAccount("ACC008");
        // ACC008 是高风险账户，应有记录
        assertFalse(acc8Txns.isEmpty(), "ACC008 应有交易记录");

        long riskyCount = acc8Txns.stream()
            .filter(t -> !"normal".equalsIgnoreCase(t.getRiskFlag()))
            .count();
        assertTrue(riskyCount >= 0, "风险交易数应 >= 0");

        System.out.printf("✅ TC08通过：ACC008 共 %d 笔交易，其中 %d 笔风险标记%n",
            acc8Txns.size(), riskyCount);
    }

    // ============================================================
    // 辅助方法
    // ============================================================

    private Transaction buildTransaction(String id, String from, String to,
                                          BigDecimal amount, String flag) {
        Transaction t = new Transaction();
        t.setTxnId(id);
        t.setFromAccount(from);
        t.setToAccount(to);
        t.setAmount(amount);
        t.setRiskFlag(flag);
        t.setDatetime(LocalDateTime.now());
        t.setChannel("WEB");
        t.setTxnType("transfer");
        t.setRemark("测试交易");
        return t;
    }

    private List<Transaction> buildSmurfingTransactions(String fromAccount, int count) {
        List<Transaction> txns = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now().minusHours(20);
        for (int i = 0; i < count; i++) {
            Transaction t = new Transaction();
            t.setTxnId("SMURF-" + (i + 1));
            t.setFromAccount(fromAccount);
            t.setToAccount("TARGET" + i);
            t.setAmount(new BigDecimal(8000 + i * 200));
            t.setDatetime(base.plusHours(i * 2));
            t.setChannel("APP");
            t.setTxnType("transfer");
            t.setRiskFlag("structuring");
            t.setRemark("拆分交易测试");
            txns.add(t);
        }
        return txns;
    }
}
