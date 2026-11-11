package com.priyani.wallet.repo;

import com.priyani.wallet.model.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface TransactionRepository extends JpaRepository<WalletTransaction, Long> {

    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

    @Query(value = "select t from WalletTransaction t "
                 + "where t.fromUserId = :userId or t.toUserId = :userId "
                 + "order by t.createdAt desc, t.id desc",
           countQuery = "select count(t) from WalletTransaction t "
                      + "where t.fromUserId = :userId or t.toUserId = :userId")
    Page<WalletTransaction> findHistory(@Param("userId") Long userId, Pageable pageable);
}
