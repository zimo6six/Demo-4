package com.bank.aml.service;

import com.bank.aml.model.AccountProfile;
import com.bank.aml.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CSV 数据加载服务
 *
 * 加载 data/transactions.csv 和 data/accounts.csv 到内存。
 * 支持从文件系统路径或 classpath 加载。
 * 应用启动时自动加载（@PostConstruct）。
 */
@Service
public class DataLoaderService {

    private static final Logger log = LoggerFactory.getLogger(DataLoaderService.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${aml.data.transactions-path:data/transactions.csv}")
    private String transactionsPath;

    @Value("${aml.data.accounts-path:data/accounts.csv}")
    private String accountsPath;

    // 内存数据缓存
    private List<Transaction> transactionCache = new ArrayList<>();
    private Map<String, AccountProfile> accountCache = new LinkedHashMap<>();

    /**
     * 应用启动时自动加载数据
     */
    @PostConstruct
    public void init() {
        log.info("AML 数据初始化开始...");
        reloadData();
    }

    /**
     * 重新加载所有数据（支持热重载）
     */
    public void reloadData() {
        try {
            transactionCache = loadTransactions(transactionsPath);
            accountCache = loadAccounts(accountsPath);
            log.info("数据加载完成：{}笔交易, {}个账户", transactionCache.size(), accountCache.size());
        } catch (Exception e) {
            log.warn("从文件系统加载失败，尝试 classpath: {}", e.getMessage());
            try {
                transactionCache = loadTransactions("classpath:data/transactions.csv");
                accountCache = loadAccounts("classpath:data/accounts.csv");
                log.info("classpath 加载完成：{}笔交易, {}个账户",
                    transactionCache.size(), accountCache.size());
            } catch (Exception e2) {
                log.error("数据加载失败，使用空数据集", e2);
                transactionCache = new ArrayList<>();
                accountCache = new LinkedHashMap<>();
            }
        }
    }

    /**
     * 获取所有交易记录（只读）
     */
    public List<Transaction> getAllTransactions() {
        return Collections.unmodifiableList(transactionCache);
    }

    /**
     * 获取所有账户画像（只读）
     */
    public Collection<AccountProfile> getAllAccounts() {
        return Collections.unmodifiableCollection(accountCache.values());
    }

    /**
     * 获取账户画像 Map（只读）
     */
    public Map<String, AccountProfile> getAccountMap() {
        return Collections.unmodifiableMap(accountCache);
    }

    /**
     * 查询指定账户的所有交易（收付两端）
     *
     * @param accountId 账户ID
     * @return 涉及该账户的交易列表（按时间升序）
     */
    public List<Transaction> getTransactionsByAccount(String accountId) {
        return transactionCache.stream()
            .filter(t -> accountId.equalsIgnoreCase(t.getFromAccount())
                      || accountId.equalsIgnoreCase(t.getToAccount()))
            .sorted(Comparator.comparing(Transaction::getDatetime,
                    Comparator.nullsLast(Comparator.naturalOrder())))
            .collect(Collectors.toList());
    }

    /**
     * 根据风险标志过滤交易
     *
     * @param riskFlag 风险标志（normal / suspicious / structuring / layering / large_normal）
     * @return 匹配的交易列表
     */
    public List<Transaction> getTransactionsByRiskFlag(String riskFlag) {
        return transactionCache.stream()
            .filter(t -> riskFlag.equalsIgnoreCase(t.getRiskFlag()))
            .collect(Collectors.toList());
    }

    // ======================================================
    // 私有加载方法
    // ======================================================

    /**
     * 从 CSV 文件加载交易记录
     */
    private List<Transaction> loadTransactions(String filePath) {
        List<Transaction> txns = new ArrayList<>();
        log.info("加载交易数据: {}", filePath);

        try (BufferedReader br = openReader(filePath)) {
            String line;
            boolean header = true;
            while ((line = br.readLine()) != null) {
                if (header) { header = false; continue; }
                String[] cols = line.split(",", -1);
                if (cols.length < 9) continue;

                Transaction txn = new Transaction();
                txn.setTxnId(cols[0].trim());
                try {
                    txn.setDatetime(LocalDateTime.parse(cols[1].trim(), DTF));
                } catch (Exception ignored) {
                    txn.setDatetime(LocalDateTime.now());
                }
                txn.setFromAccount(cols[2].trim());
                txn.setToAccount(cols[3].trim());
                txn.setAmount(new BigDecimal(cols[4].trim()));
                txn.setChannel(cols[5].trim());
                txn.setRemark(cols[6].trim());
                txn.setTxnType(cols[7].trim());
                txn.setRiskFlag(cols[8].trim());
                txns.add(txn);
            }
        } catch (Exception e) {
            log.error("加载交易数据失败: {}", filePath, e);
        }

        txns.sort(Comparator.comparing(Transaction::getDatetime,
                  Comparator.nullsLast(Comparator.naturalOrder())));
        log.info("成功加载 {} 笔交易记录", txns.size());
        return txns;
    }

    /**
     * 从 CSV 文件加载账户画像
     */
    private Map<String, AccountProfile> loadAccounts(String filePath) {
        Map<String, AccountProfile> accounts = new LinkedHashMap<>();
        log.info("加载账户数据: {}", filePath);

        try (BufferedReader br = openReader(filePath)) {
            String line;
            boolean header = true;
            while ((line = br.readLine()) != null) {
                if (header) { header = false; continue; }
                String[] cols = line.split(",", -1);
                if (cols.length < 6) continue;

                AccountProfile acc = new AccountProfile(
                    cols[0].trim(), cols[1].trim(), cols[2].trim(),
                    cols[3].trim(), Double.parseDouble(cols[4].trim()), cols[5].trim()
                );
                accounts.put(acc.getId(), acc);
            }
        } catch (Exception e) {
            log.error("加载账户数据失败: {}", filePath, e);
        }

        log.info("成功加载 {} 个账户画像", accounts.size());
        return accounts;
    }

    private BufferedReader openReader(String filePath) throws Exception {
        if (filePath.startsWith("classpath:")) {
            String cp = filePath.substring("classpath:".length());
            return new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(
                    getClass().getClassLoader().getResourceAsStream(cp),
                    "classpath resource not found: " + cp)));
        }
        return new BufferedReader(new FileReader(filePath));
    }
}
