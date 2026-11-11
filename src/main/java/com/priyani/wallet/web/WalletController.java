package com.priyani.wallet.web;

import com.priyani.wallet.service.WalletService;
import com.priyani.wallet.web.Dtos.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class WalletController {

    private final WalletService service;

    public WalletController(WalletService service) {
        this.service = service;
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest req) {
        return UserResponse.from(service.register(req.name(), req.email()));
    }

    @GetMapping("/users/{id}/wallet")
    public WalletResponse wallet(@PathVariable Long id) {
        return WalletResponse.from(service.getWallet(id));
    }

    @PostMapping("/users/{id}/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse deposit(@PathVariable Long id, @Valid @RequestBody AmountRequest req) {
        return TransactionResponse.from(service.deposit(id, req.amount()));
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse transfer(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                        @Valid @RequestBody TransferRequest req) {
        return TransactionResponse.from(
                service.transfer(req.fromUserId(), req.toUserId(), req.amount(), idempotencyKey));
    }

    @GetMapping("/users/{id}/transactions")
    public PageResponse<TransactionResponse> history(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(service.history(id, page, size), TransactionResponse::from);
    }
}
