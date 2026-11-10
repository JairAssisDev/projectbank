package com.digitalbank.account.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.digitalbank.account.domain.model.Account;

/**
 * Prova que o débito atômico não perde atualizações nem deixa o saldo negativo
 * quando centenas de virtual threads debitam a MESMA conta ao mesmo tempo.
 */
@DataJpaTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:concurrency;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=20000",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.hikari.maximum-pool-size=20"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AccountDebitConcurrencyTest {

    @Autowired
    AccountRepository accounts;
    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void concurrentDebitsNeverOverdraftNorLoseUpdates() throws Exception {
        var tx = new TransactionTemplate(transactionManager);
        var initial = new BigDecimal("100.00");
        var account = tx.execute(s -> accounts.save(
                Account.open("Concorrência", UUID.randomUUID().toString().substring(0, 20), "hash", initial,
                        Instant.now())));

        int attempts = 300;                       // 300 débitos de R$1,00 contra saldo de R$100,00
        var amount = new BigDecimal("1.00");
        var start = new CountDownLatch(1);
        var successes = new AtomicInteger();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<?>>();
            for (int i = 0; i < attempts; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    Integer updated = tx.execute(s -> accounts.debit(account.getId(), amount, Instant.now()));
                    if (updated != null && updated == 1) {
                        successes.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var f : futures) {
                f.get();
            }
        }

        var finalBalance = accounts.findById(account.getId()).orElseThrow().getBalance();
        assertThat(successes.get()).isEqualTo(100);
        assertThat(finalBalance).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(initial.subtract(amount.multiply(BigDecimal.valueOf(successes.get()))))
                .isEqualByComparingTo(finalBalance);
    }
}
