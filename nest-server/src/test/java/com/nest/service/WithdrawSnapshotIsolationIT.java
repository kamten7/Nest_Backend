package com.nest.service;

import com.nest.constant.RentOrderStatus;
import com.nest.entity.Wallet;
import com.nest.order.mapper.RentOrderMapper;
import com.nest.wallet.mapper.WalletMapper;
import com.nest.wallet.service.impl.WalletServiceImpl;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 提现「快照读 vs 当前读」集成测试（需要真实 MySQL，连不上时自动跳过）。
 */
class WithdrawSnapshotIsolationIT {

    private static final long WALLET_ID = 990100L;
    private static final long ORDER_ID = 990100L;
    private static final long LANDLORD_ID = 990100L;
    private static final BigDecimal DEPOSIT = new BigDecimal("3000.00");

    private static PooledDataSource dataSource;
    private static SqlSessionFactory sqlSessionFactory;

    private record Outcome(BigDecimal locked, int withdrawnRows) {
    }

    @BeforeAll
    static void setUp() throws Exception {
        Map<String, Object> dev = devDatasource();
        String host = env("NEST_IT_MYSQL_HOST", str(dev.get("host"), "127.0.0.1"));
        String port = env("NEST_IT_MYSQL_PORT", str(dev.get("port"), "3309"));
        String user = env("NEST_IT_MYSQL_USER", str(dev.get("username"), "root"));
        String password = env("NEST_IT_MYSQL_PASSWORD", str(dev.get("password"), ""));
        String database = str(dev.get("database"), "nest_rent");
        String url = "jdbc:mysql://" + host + ":" + port
                + "/" + database + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
        try (Connection probe = DriverManager.getConnection(url, user, password)) {
            assertThat(probe.isValid(2)).isTrue();
        } catch (Exception e) {
            Assumptions.abort("MySQL 不可达，跳过集成测试: " + url + "（设置 NEST_IT_MYSQL_PASSWORD 等环境变量后重跑）");
        }

        dataSource = new PooledDataSource("com.mysql.cj.jdbc.Driver", url, user, password);
        dataSource.setPoolMaximumActiveConnections(4);

        Configuration configuration = new Configuration(
                new Environment("withdraw-it", new JdbcTransactionFactory(), dataSource));
        configuration.getTypeAliasRegistry().registerAliases("com.nest.entity");
        for (String resource : new String[]{"mapper/WalletMapper.xml", "mapper/RentOrderMapper.xml"}) {
            try (InputStream in = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(in, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null) {
            dataSource.forceCloseAll();
        }
    }

    @BeforeEach
    void resetFixtures() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("DELETE FROM rent_order WHERE id = " + ORDER_ID);
            st.execute("DELETE FROM wallet WHERE id = " + WALLET_ID);
            st.execute("INSERT INTO wallet (id, user_type, user_id, balance, status) VALUES ("
                    + WALLET_ID + ", 'landlord', " + LANDLORD_ID + ", 0.00, 1)");
            st.execute("INSERT INTO rent_order (id, order_no, tenant_id, house_id, landlord_id,"
                    + " deposit, monthly_rent, status) VALUES ("
                    + ORDER_ID + ", 'IT-WD-SNAP-001', 990101, 990102, " + LANDLORD_ID
                    + ", 3000.00, 2000.00, 1)");
        }
    }

    @AfterEach
    void cleanFixtures() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("DELETE FROM rent_order WHERE id = " + ORDER_ID);
            st.execute("DELETE FROM wallet WHERE id = " + WALLET_ID);
        }
    }

    @Test
    void withdrawMustDeclareReadCommittedIsolation() throws Exception {
        Transactional annotation = WalletServiceImpl.class
                .getMethod("withdraw", String.class, Long.class, BigDecimal.class, String.class)
                .getAnnotation(Transactional.class);
        assertThat(annotation)
                .as("withdraw 必须显式声明隔离级别：默认 RR 下 sumLockedAmount 是快照读，会读旧 locked")
                .isNotNull();
        assertThat(annotation.isolation()).isEqualTo(Isolation.READ_COMMITTED);
    }

    @Test
    void readCommitted_lockedAmountSeesDepositCommittedAfterFirstSnapshotRead() throws Exception {
        Outcome outcome = runWithdrawStatementSequence(Connection.TRANSACTION_READ_COMMITTED);
        assertThat(outcome.locked()).isEqualByComparingTo(DEPOSIT);
        assertThat(outcome.withdrawnRows())
                .as("locked 看到已提交押金后，balance - locked = 0，押金提不走")
                .isZero();
    }

    @Test
    void repeatableRead_staleSnapshotLetsLandlordWithdrawLockedDeposit() throws Exception {
        Outcome outcome = runWithdrawStatementSequence(Connection.TRANSACTION_REPEATABLE_READ);
        assertThat(outcome.locked())
                .as("RR 下 sumLockedAmount 复用第一条快照读的 read view，看不到刚提交的 status=2")
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(outcome.withdrawnRows())
                .as("历史 bug 特征：locked=0 使条件扣款放行，押金被提穿（withdraw 因此不得跑在 RR 下）")
                .isEqualTo(1);
    }

    /**
     * 按 withdraw 的真实语句顺序在单连接上重放：
     * 快照读钱包 → （并发缴押金事务提交）→ FOR UPDATE 当前读 → 快照读 locked → 条件扣款。
     * 事务最终回滚，不污染断言之外的数据。
     */
    private Outcome runWithdrawStatementSequence(int isolation) throws Exception {
        try (Connection connA = dataSource.getConnection()) {
            connA.setAutoCommit(false);
            connA.setTransactionIsolation(isolation);
            try (SqlSession sessionA = sqlSessionFactory.openSession(connA)) {
                WalletMapper walletMapper = sessionA.getMapper(WalletMapper.class);
                RentOrderMapper orderMapper = sessionA.getMapper(RentOrderMapper.class);

                Wallet wallet = walletMapper.selectByUser("landlord", LANDLORD_ID);

                payDepositInAnotherTransaction();

                Wallet lockedRow = walletMapper.lockById(wallet.getId());
                BigDecimal locked = orderMapper.sumLockedAmount(LANDLORD_ID, LocalDate.now(),
                        RentOrderStatus.DEPOSIT_LOCKED_STATUS, RentOrderStatus.TERMINATING);
                int rows = walletMapper.decreaseBalanceWithLock(lockedRow.getId(), DEPOSIT, locked);

                connA.rollback();
                return new Outcome(locked, rows);
            }
        }
    }

    /** 模拟租客缴押金：加余额与订单 status 1→2 在同一事务提交（与 payDeposit 一致）。 */
    private void payDepositInAnotherTransaction() throws Exception {
        try (Connection connB = dataSource.getConnection()) {
            connB.setAutoCommit(false);
            try (SqlSession sessionB = sqlSessionFactory.openSession(connB)) {
                sessionB.getMapper(WalletMapper.class).increaseBalance(WALLET_ID, DEPOSIT);
                sessionB.getMapper(RentOrderMapper.class)
                        .activateAfterDeposit(ORDER_ID, LocalDate.now(), YearMonth.now().toString());
                connB.commit();
            }
        }
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /** IDE 直接运行时没有环境变量，退而读本地 gitignore 的 application-dev.yml；读不到则全走默认值。 */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> devDatasource() {
        try (InputStream in = Resources.getResourceAsStream("application-dev.yml")) {
            Map<String, Object> root = new Yaml().load(in);
            Object nest = root == null ? null : root.get("nest");
            Object datasource = nest instanceof Map<?, ?> nestMap ? nestMap.get("datasource") : null;
            return datasource instanceof Map<?, ?> dsMap ? (Map<String, Object>) dsMap : Map.of();
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String str(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }
}
