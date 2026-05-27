package vn.edu.usth.tip.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.usth.tip.backend.dto.transaction.CreateTransactionRequest;
import vn.edu.usth.tip.backend.dto.transaction.SyncRequest;
import vn.edu.usth.tip.backend.dto.transaction.SyncResponse;
import vn.edu.usth.tip.backend.dto.transaction.SyncTransactionRequest;
import vn.edu.usth.tip.backend.dto.transaction.TransactionResponse;
import vn.edu.usth.tip.backend.exception.ResourceNotFoundException;
import vn.edu.usth.tip.backend.models.*;
import vn.edu.usth.tip.backend.models.enums.TransactionType;
import vn.edu.usth.tip.backend.repositories.*;
import vn.edu.usth.tip.backend.utils.SecurityUtils;
import vn.edu.usth.tip.backend.utils.SyncConstants;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import vn.edu.usth.tip.backend.dto.common.DeltaResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final GoalRepository goalRepository;
    private final SecurityUtils securityUtils;

    @Transactional
    public TransactionResponse createTransaction(CreateTransactionRequest req) {
        Account account = accountRepository.findById(req.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", req.getAccountId()));

        Transaction tx = new Transaction();
        tx.setUser(userRepository.findById(req.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", req.getUserId())));
        tx.setAccount(account);
        tx.setCategory(categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", req.getCategoryId())));
        if (req.getGoalId() != null) {
            tx.setGoal(goalRepository.findById(req.getGoalId())
                    .orElseThrow(() -> new ResourceNotFoundException("Goal", "id", req.getGoalId())));
        }
        tx.setAmount(req.getAmount());
        tx.setType(req.getType());
        tx.setNote(req.getNote());
        tx.setTransactionDate(req.getTransactionDate());
        tx.setReceiptUrl(req.getReceiptUrl());
        tx.setIsRecurring(req.getIsRecurring() != null ? req.getIsRecurring() : false);
        tx.setRecurInterval(req.getRecurInterval());

        BigDecimal amount = req.getAmount();
        BigDecimal currentBalance = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
        if (TransactionType.income == req.getType()) {
            account.setBalance(currentBalance.add(amount));
        } else if (TransactionType.expense == req.getType() || TransactionType.transfer == req.getType()) {
            account.setBalance(currentBalance.subtract(amount));
        }
        accountRepository.save(account);

        return toResponse(transactionRepository.save(tx));
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByUser(UUID userId) {
        return transactionRepository.findByUser_Id(userId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getAllTransactions() {
        User user = securityUtils.getCurrentUser();
        return getTransactionsByUser(user.getId());
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(UUID id) {
        return toResponse(transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id)));
    }

    @Transactional
    public TransactionResponse updateTransaction(UUID id, CreateTransactionRequest req) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));

        // Reverse the old account's balance before applying new values
        // so account switching within an edit is handled correctly.
        Account oldAccount = tx.getAccount();
        if (oldAccount != null && tx.getAmount() != null) {
            BigDecimal current = oldAccount.getBalance() != null ? oldAccount.getBalance() : BigDecimal.ZERO;
            if (TransactionType.income == tx.getType()) {
                oldAccount.setBalance(current.subtract(tx.getAmount()));
            } else if (TransactionType.expense == tx.getType() || TransactionType.transfer == tx.getType()) {
                oldAccount.setBalance(current.add(tx.getAmount()));
            }
            accountRepository.save(oldAccount);
        }

        tx.setAmount(req.getAmount());
        tx.setType(req.getType());
        tx.setNote(req.getNote());
        tx.setTransactionDate(req.getTransactionDate());
        tx.setReceiptUrl(req.getReceiptUrl());
        tx.setIsRecurring(req.getIsRecurring() != null ? req.getIsRecurring() : false);
        tx.setRecurInterval(req.getRecurInterval());
        if (req.getCategoryId() != null) {
            tx.setCategory(categoryRepository.findById(req.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", req.getCategoryId())));
        }

        Account newAccount = (req.getAccountId() != null)
                ? accountRepository.findById(req.getAccountId())
                        .orElseThrow(() -> new ResourceNotFoundException("Account", "id", req.getAccountId()))
                : oldAccount;
        tx.setAccount(newAccount);

        if (newAccount != null) {
            BigDecimal current = newAccount.getBalance() != null ? newAccount.getBalance() : BigDecimal.ZERO;
            if (TransactionType.income == req.getType()) {
                newAccount.setBalance(current.add(req.getAmount()));
            } else if (TransactionType.expense == req.getType() || TransactionType.transfer == req.getType()) {
                newAccount.setBalance(current.subtract(req.getAmount()));
            }
            accountRepository.save(newAccount);
        }

        return toResponse(transactionRepository.save(tx));
    }

    @Transactional
    public void deleteTransaction(UUID id) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));
        Account account = tx.getAccount();
        if (account != null && tx.getAmount() != null && tx.getDeletedAt() == null) {
            BigDecimal currentBalance = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
            if (TransactionType.income == tx.getType()) {
                account.setBalance(currentBalance.subtract(tx.getAmount()));
            } else if (TransactionType.expense == tx.getType() || TransactionType.transfer == tx.getType()) {
                account.setBalance(currentBalance.add(tx.getAmount()));
            }
            accountRepository.save(account);
        }
        OffsetDateTime now = OffsetDateTime.now();
        tx.setDeletedAt(now);
        tx.setUpdatedAt(now);
        tx.setClientSync(true);
        transactionRepository.save(tx);
    }

    // Batch sync API — Last-Write-Wins: PATH DELETE → PATH A (edit) → PATH B (new).

    @Transactional(rollbackFor = Exception.class)
    public SyncResponse syncTransactions(SyncRequest req) {
        User user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", req.getUserId()));

        List<TransactionResponse> responseList = new ArrayList<>();
        int savedCount = 0;
        int skippedCount = 0;

        for (SyncTransactionRequest item : req.getTransactions()) {
            if (item.isDeleted() && item.getTransactionId() != null) {
                handleDeletedSync(item, user, responseList);
                continue;
            }

            if (item.getTransactionId() != null) {
                Integer pathAResult = handleExistingSync(item, user, responseList);
                if (pathAResult != null) {
                    savedCount += pathAResult;
                    continue;
                }
                // Not found on server → fall through to PATH B with this UUID
            }

            int pathBResult = handleNewSync(item, user, responseList);
            if (pathBResult > 0) {
                savedCount++;
            } else {
                skippedCount++;
            }
        }

        return new SyncResponse(savedCount, skippedCount, responseList);
    }

    private void handleDeletedSync(SyncTransactionRequest item, User user,
                                   List<TransactionResponse> responseList) {
        transactionRepository.findByIdAndUser_Id(item.getTransactionId(), user.getId())
                .ifPresent(existing -> {
                    if (existing.getDeletedAt() == null) {
                        reverseBalanceEffect(existing.getAccount(), existing.getType(), existing.getAmount());
                        OffsetDateTime nowDel = OffsetDateTime.now();
                        existing.setDeletedAt(nowDel);
                        existing.setUpdatedAt(nowDel);
                        existing.setClientSync(true);
                        transactionRepository.save(existing);
                    }
                });
        // Must ACK even when already deleted — otherwise Android retries forever (zombie sync loop)
        TransactionResponse ack = new TransactionResponse();
        ack.setId(item.getTransactionId());
        ack.setDeleted(true);
        responseList.add(ack);
    }

    /**
     * Returns null if the transaction is not found on the server (fall through to PATH B).
     * Returns 0 if server wins the LWW conflict (no save). Returns 1 if client wins (saved).
     */
    private Integer handleExistingSync(SyncTransactionRequest item, User user,
                                       List<TransactionResponse> responseList) {
        java.util.Optional<Transaction> existingOpt =
                transactionRepository.findByIdAndUser_Id(item.getTransactionId(), user.getId());
        if (existingOpt.isEmpty()) return null;

        Transaction existing = existingOpt.get();
        // Null-safe LWW: clientUpdatedAt=null → Server Wins (legacy client without timestamps)
        // existing.updatedAt=null → Client Wins (legacy record inserted before updatedAt was tracked)
        boolean clientWins = item.getClientUpdatedAt() != null
                && (existing.getUpdatedAt() == null
                    || item.getClientUpdatedAt().isAfter(existing.getUpdatedAt()));
        if (!clientWins) {
            // Return server version so Android exits the Sync Blackhole
            responseList.add(toResponse(existing));
            return 0;
        }

        reverseBalanceEffect(existing.getAccount(), existing.getType(), existing.getAmount());
        Account newAccount = item.getAccountId() != null
                ? accountRepository.findById(item.getAccountId()).orElse(existing.getAccount())
                : existing.getAccount();
        existing.setAccount(newAccount);
        if (item.getCategoryId() != null) {
            existing.setCategory(categoryRepository.findById(item.getCategoryId())
                    .orElse(existing.getCategory()));
        }
        BigDecimal positiveAmount = item.getAmount().abs();
        existing.setAmount(positiveAmount);
        existing.setType(item.getType());
        existing.setNote(item.getNote());
        existing.setTransactionDate(item.getTransactionDate());
        existing.setIsRecurring(item.getIsRecurring() != null ? item.getIsRecurring() : false);
        existing.setRecurInterval(item.getRecurInterval());
        applyBalanceEffect(newAccount, item.getType(), positiveAmount);
        // Use client timestamp as ground truth — disables @PreUpdate auto-stamp
        existing.setUpdatedAt(item.getClientUpdatedAt());
        existing.setClientSync(true);
        transactionRepository.save(existing);
        responseList.add(toResponse(existing));
        return 1;
    }

    /** Returns 1 if a new transaction was inserted, 0 if skipped (missing fields or duplicate). */
    private int handleNewSync(SyncTransactionRequest item, User user,
                              List<TransactionResponse> responseList) {
        if (item.getAccountId() == null || item.getCategoryId() == null || item.getAmount() == null) {
            return 0;
        }

        Account account = accountRepository.findById(item.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", item.getAccountId()));
        Category category = categoryRepository.findById(item.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", item.getCategoryId()));

        if (item.getTransactionId() != null) {
            // Client UUID not found in PATH A → INSERT with that UUID (trust client UUID, collision ≈ 0%)
            Transaction newTx = buildNewTransaction(user, account, category, item);
            newTx.setId(item.getTransactionId());
            applyBalanceEffect(account, item.getType(), item.getAmount().abs());
            accountRepository.save(account);
            transactionRepository.save(newTx);
            responseList.add(toResponse(newTx));
            return 1;
        }

        // Legacy path (transactionId=null) — check duplicate to prevent double-send
        java.util.Optional<Transaction> duplicate = transactionRepository.findDuplicate(
                user.getId(), item.getAmount(), item.getTransactionDate(),
                item.getType() != null ? item.getType().name() : null,
                category.getName(), item.getNote());
        if (duplicate.isPresent()) {
            // Return existing record so Android syncs the canonical UUID and exits the loop
            responseList.add(toResponse(duplicate.get()));
            return 0;
        }

        Transaction newTx = buildNewTransaction(user, account, category, item);
        newTx.setId(UUID.randomUUID()); // server assigns UUID for legacy records
        applyBalanceEffect(account, item.getType(), item.getAmount().abs());
        accountRepository.save(account);
        transactionRepository.save(newTx);
        responseList.add(toResponse(newTx));
        return 1;
    }

    private Transaction buildNewTransaction(User user, Account account, Category category,
                                             SyncTransactionRequest item) {
        Transaction tx = new Transaction();
        tx.setUser(user);
        tx.setAccount(account);
        tx.setCategory(category);
        if (item.getGoalId() != null) {
            tx.setGoal(goalRepository.findById(item.getGoalId()).orElse(null));
        }
        tx.setAmount(item.getAmount().abs());
        tx.setType(item.getType());
        tx.setNote(item.getNote());
        tx.setTransactionDate(item.getTransactionDate());
        tx.setReceiptUrl(item.getReceiptUrl());
        tx.setIsRecurring(item.getIsRecurring() != null ? item.getIsRecurring() : false);
        tx.setRecurInterval(item.getRecurInterval());
        tx.setCreatedAt(item.getCreatedAt() != null ? item.getCreatedAt() : OffsetDateTime.now());
        if (item.getClientUpdatedAt() != null) {
            tx.setUpdatedAt(item.getClientUpdatedAt());
            tx.setClientSync(true);
        }
        return tx;
    }

    private void reverseBalanceEffect(Account account, TransactionType type, BigDecimal amount) {
        if (account == null || amount == null) return;
        BigDecimal bal = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
        if (type == TransactionType.income) {
            account.setBalance(bal.subtract(amount));
        } else {
            account.setBalance(bal.add(amount));
        }
        accountRepository.save(account);
    }

    private void applyBalanceEffect(Account account, TransactionType type, BigDecimal amount) {
        if (account == null || amount == null) return;
        BigDecimal bal = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
        if (type == TransactionType.income) {
            account.setBalance(bal.add(amount));
        } else {
            account.setBalance(bal.subtract(amount));
        }
        accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getRecentTransactions(UUID userId, int days) {
        // Use transactionDate (LocalDate) instead of createdAt (OffsetDateTime) to avoid
        // a Hibernate/Neon PostgreSQL timezone binding issue that caused HTTP 500 errors
        LocalDate since = LocalDate.now().minusDays(days);
        return transactionRepository
                .findByUser_IdAndTransactionDateGreaterThanEqualOrderByTransactionDateDescCreatedAtDesc(
                        userId, since)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getMyRecentTransactions(int days) {
        User user = securityUtils.getCurrentUser();
        return getRecentTransactions(user.getId(), days);
    }

    private TransactionResponse toResponse(Transaction tx) {
        TransactionResponse res = new TransactionResponse();
        res.setId(tx.getId());
        res.setUserId(tx.getUser().getId());
        res.setAccountId(tx.getAccount().getId());
        res.setAccountName(tx.getAccount().getName());
        res.setCategoryId(tx.getCategory().getId());
        res.setCategoryName(tx.getCategory().getName());
        res.setGoalId(tx.getGoal() != null ? tx.getGoal().getId() : null);
        res.setAmount(tx.getAmount());
        res.setType(tx.getType());
        res.setNote(tx.getNote());
        res.setTransactionDate(tx.getTransactionDate());
        res.setReceiptUrl(tx.getReceiptUrl());
        res.setIsRecurring(tx.getIsRecurring());
        res.setRecurInterval(tx.getRecurInterval());
        res.setCreatedAt(tx.getCreatedAt());
        res.setUpdatedAt(tx.getUpdatedAt());
        res.setDeleted(tx.getDeletedAt() != null);
        return res;
    }

    @Transactional(readOnly = true)
    public DeltaResponse<TransactionResponse> getDelta(String updatedSince, String untilTimestamp,
                                                        String lastUpdatedAt, UUID lastId, int limit) {
        User user = securityUtils.getCurrentUser();
        OffsetDateTime since = updatedSince != null
                ? OffsetDateTime.parse(updatedSince)
                : OffsetDateTime.parse(SyncConstants.EPOCH_CURSOR);
        OffsetDateTime until = untilTimestamp != null
                ? OffsetDateTime.parse(untilTimestamp)
                : OffsetDateTime.now();
        OffsetDateTime cursorTs = lastUpdatedAt != null ? OffsetDateTime.parse(lastUpdatedAt) : null;
        Slice<Transaction> slice = transactionRepository.findDelta(
                user.getId(), since, until, cursorTs, lastId, PageRequest.of(0, limit));
        return new DeltaResponse<>(
                slice.getContent().stream().map(this::toResponse).toList(),
                slice.hasNext(),
                until.toString());
    }
}