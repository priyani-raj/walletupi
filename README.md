# Wallet API

A mini payments wallet REST API: register users, add funds, transfer money, view history.

**Stack:** Java 17, Spring Boot 3, Spring Data JPA, MySQL (H2 for local/tests), JUnit 5

## Key design points
- **Atomic transfers:** debit, credit, and ledger entry happen in one `@Transactional` unit.
- **Idempotency:** transfers require an `Idempotency-Key` header; retries return the original result and never charge twice (unique constraint in the database).
- **Concurrency safety:** wallet rows are locked with `SELECT ... FOR UPDATE`, in ascending user-id order to avoid deadlocks.
- **Replay protection:** reusing an `Idempotency-Key` with a *different* from/to/amount is rejected (422) instead of silently returning the old result.
- **Validation and error codes:** 400 invalid input, 404 unknown user, 409 duplicate, 422 insufficient balance.
- **Pagination:** transaction history is paged (`page`, `size` up to 100), newest first.
- **Tested:** service tests (atomicity, idempotency, 20-thread concurrency) and MockMvc API tests (status codes, validation, paging), run in CI.

## Run
```
mvn spring-boot:run                                   # in-memory H2, zero setup
mvn spring-boot:run -Dspring-boot.run.profiles=mysql  # MySQL (create database `wallet` first)
mvn test
docker compose up --build                              # API + MySQL in containers
```

## Endpoints
| Method | Path | Notes |
|---|---|---|
| POST | /api/users | `{"name","email"}` |
| GET | /api/users/{id}/wallet | balance |
| POST | /api/users/{id}/deposit | `{"amount"}` |
| POST | /api/transfers | header `Idempotency-Key`, body `{"fromUserId","toUserId","amount"}` |
| GET | /api/users/{id}/transactions?page=0&size=20 | paged history, newest first: `{items, page, size, totalElements, totalPages}` |

## Try it
```
curl -X POST localhost:8080/api/users -H "Content-Type: application/json" -d '{"name":"A","email":"a@x.com"}'
curl -X POST localhost:8080/api/users -H "Content-Type: application/json" -d '{"name":"B","email":"b@x.com"}'
curl -X POST localhost:8080/api/users/1/deposit -H "Content-Type: application/json" -d '{"amount":100.00}'
curl -X POST localhost:8080/api/transfers -H "Content-Type: application/json" -H "Idempotency-Key: abc-123" \
     -d '{"fromUserId":1,"toUserId":2,"amount":25.00}'
```

## Postman
Import `postman/wallet-api.postman_collection.json` and run the collection: it registers two users, deposits, transfers and asserts the balances.

## Next steps
- JWT authentication (users should only move their own money)
- Per-currency wallets and an audit/ledger table
