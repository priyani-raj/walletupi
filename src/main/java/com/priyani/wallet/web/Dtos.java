package com.priyani.wallet.web;

import com.priyani.wallet.model.*;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

public final class Dtos {

    private Dtos() {}

    public record RegisterRequest(
            @NotBlank String name,
            @NotBlank @Email String email) {}

    public record AmountRequest(
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount) {}

    public record TransferRequest(
            @NotNull Long fromUserId,
            @NotNull Long toUserId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount) {}

    public record UserResponse(Long id, String name, String email) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getName(), u.getEmail());
        }
    }

    public record WalletResponse(Long userId, BigDecimal balance) {
        public static WalletResponse from(Wallet w) {
            return new WalletResponse(w.getUserId(), w.getBalance());
        }
    }

    public record PageResponse<T>(List<T> items, int page, int size,
                                  long totalElements, int totalPages) {
        public static <S, T> PageResponse<T> from(Page<S> p, Function<S, T> mapper) {
            return new PageResponse<>(p.getContent().stream().map(mapper).toList(),
                    p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
        }
    }

    public record TransactionResponse(Long id, TransactionType type, Long fromUserId,
                                      Long toUserId, BigDecimal amount, Instant createdAt) {
        public static TransactionResponse from(WalletTransaction t) {
            return new TransactionResponse(t.getId(), t.getType(), t.getFromUserId(),
                    t.getToUserId(), t.getAmount(), t.getCreatedAt());
        }
    }
}
