package com.priyani.wallet;

import com.priyani.wallet.model.WalletTransaction;
import com.priyani.wallet.service.ApiException;
import com.priyani.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class WalletServiceTest {

    @Autowired
    WalletService service;

    private Long newUser() {
        return service.register("User", UUID.randomUUID() + "@test.com").getId();
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void transferMovesMoneyBetweenWallets() {
        Long a = newUser(), b = newUser();
        service.deposit(a, bd("100.00"));

        service.transfer(a, b, bd("30.00"), UUID.randomUUID().toString());

        assertEquals(0, bd("70.00").compareTo(service.getWallet(a).getBalance()));
        assertEquals(0, bd("30.00").compareTo(service.getWallet(b).getBalance()));
    }

    @Test
    void transferFailsWhenBalanceIsInsufficient() {
        Long a = newUser(), b = newUser();
        service.deposit(a, bd("10.00"));

        assertThrows(ApiException.class,
                () -> service.transfer(a, b, bd("50.00"), UUID.randomUUID().toString()));

        assertEquals(0, bd("10.00").compareTo(service.getWallet(a).getBalance()));
        assertEquals(0, BigDecimal.ZERO.compareTo(service.getWallet(b).getBalance()));
    }

    @Test
    void retryWithSameIdempotencyKeyDoesNotChargeTwice() {
        Long a = newUser(), b = newUser();
        service.deposit(a, bd("100.00"));
        String key = UUID.randomUUID().toString();

        WalletTransaction first = service.transfer(a, b, bd("40.00"), key);
        WalletTransaction retry = service.transfer(a, b, bd("40.00"), key);

        assertEquals(first.getId(), retry.getId());
        assertEquals(0, bd("60.00").compareTo(service.getWallet(a).getBalance()));
        assertEquals(0, bd("40.00").compareTo(service.getWallet(b).getBalance()));
    }

    @Test
    void sameIdempotencyKeyWithDifferentAmountIsRejected() {
        Long a = newUser(), b = newUser();
        service.deposit(a, bd("100.00"));
        String key = UUID.randomUUID().toString();

        service.transfer(a, b, bd("10.00"), key);

        assertThrows(ApiException.class, () -> service.transfer(a, b, bd("20.00"), key));
        assertEquals(0, bd("90.00").compareTo(service.getWallet(a).getBalance()));
    }

    @Test
    void concurrentTransfersNeverOverdrawWallet() throws Exception {
        Long a = newUser(), b = newUser();
        service.deposit(a, bd("100.00"));

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        Future<?>[] futures = new Future<?>[threads];

        for (int i = 0; i < threads; i++) {
            futures[i] = pool.submit(() -> {
                start.await();
                try {
                    service.transfer(a, b, bd("10.00"), UUID.randomUUID().toString());
                    succeeded.incrementAndGet();
                } catch (ApiException expected) {
                    // insufficient balance once the wallet is empty
                }
                return null;
            });
        }
        start.countDown();
        for (Future<?> f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(10, succeeded.get());
        assertEquals(0, BigDecimal.ZERO.compareTo(service.getWallet(a).getBalance()));
        assertEquals(0, bd("100.00").compareTo(service.getWallet(b).getBalance()));
    }
}
