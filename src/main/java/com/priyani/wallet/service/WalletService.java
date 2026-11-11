package com.priyani.wallet.service;

import com.priyani.wallet.model.*;
import com.priyani.wallet.repo.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class WalletService {

    private final UserRepository users;
    private final WalletRepository wallets;
    private final TransactionRepository transactions;

    public WalletService(UserRepository users, WalletRepository wallets,
                         TransactionRepository transactions) {
        this.users = users;
        this.wallets = wallets;
        this.transactions = transactions;
    }

    @Transactional
    public User register(String name, String email) {
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "Email already registered");
        }
        User user = users.save(new User(name, email));
        wallets.save(new Wallet(user.getId()));
        return user;
    }

    @Transactional(readOnly = true)
    public Wallet getWallet(Long userId) {
        return wallets.findByUserId(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found: " + userId));
    }

    @Transactional
    public WalletTransaction deposit(Long userId, BigDecimal amount) {
        Wallet wallet = lockWallet(userId);
        wallet.setBalance(wallet.getBalance().add(amount));
        return transactions.saveAndFlush(
                new WalletTransaction(null, userId, amount, TransactionType.DEPOSIT, null));
    }

    /**
     * Atomically moves money between two wallets.
     * - @Transactional: debit + credit + record all succeed or all roll back.
     * - Idempotency key: a retried request returns the original result, no double charge.
     * - Row locks taken in ascending user-id order, so two opposite transfers can't deadlock.
     */
    @Transactional
    public WalletTransaction transfer(Long fromUserId, Long toUserId,
                                      BigDecimal amount, String idempotencyKey) {
        if (fromUserId.equals(toUserId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot transfer to the same user");
        }

        Optional<WalletTransaction> previous = transactions.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            WalletTransaction original = previous.get();
            // A key may only be replayed with the same request; otherwise the client has a bug.
            boolean sameRequest = fromUserId.equals(original.getFromUserId())
                    && toUserId.equals(original.getToUserId())
                    && amount.compareTo(original.getAmount()) == 0;
            if (!sameRequest) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Idempotency-Key was already used with a different request");
            }
            return original;
        }

        Long firstId = Math.min(fromUserId, toUserId);
        Long secondId = Math.max(fromUserId, toUserId);
        Wallet first = lockWallet(firstId);
        Wallet second = lockWallet(secondId);
        Wallet from = fromUserId.equals(firstId) ? first : second;
        Wallet to = fromUserId.equals(firstId) ? second : first;

        if (from.getBalance().compareTo(amount) < 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient balance");
        }

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        // saveAndFlush so a duplicate idempotency key fails here and rolls everything back.
        return transactions.saveAndFlush(
                new WalletTransaction(fromUserId, toUserId, amount,
                        TransactionType.TRANSFER, idempotencyKey));
    }

    @Transactional(readOnly = true)
    public Page<WalletTransaction> history(Long userId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "page must be >= 0 and size must be between 1 and 100");
        }
        getWallet(userId); // 404 if the user doesn't exist
        return transactions.findHistory(userId, PageRequest.of(page, size));
    }

    private Wallet lockWallet(Long userId) {
        return wallets.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found: " + userId));
    }
}
