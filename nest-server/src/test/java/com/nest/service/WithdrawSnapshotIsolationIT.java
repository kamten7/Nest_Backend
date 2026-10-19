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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class WithdrawSnapshotIsolationIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("nest_rent")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci");

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
        try (Connection conn = DriverManager.getConnection(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/schema.sql"));
        }

        dataSource = new PooledDataSource(
                MYSQL.getDriverClassName(), MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
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
}
