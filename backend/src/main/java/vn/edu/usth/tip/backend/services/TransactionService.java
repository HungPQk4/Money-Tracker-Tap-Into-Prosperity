package vn.edu.usth.tip.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @Transactional
    public TransactionResponse createTransaction(CreateTransactionRequest req) {
        Transaction tx = new Transaction();
        tx.setUser(userRepository.findById(req.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", req.getUserId())));
        Account account = accountRepository.findById(req.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", req.getAccountId()));
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

        // ─── Cập nhật số dư account ───────────────────────────────────────
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
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
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

        // ── Hoàn nguyên số dư ví CŨ trước khi update ──────────────────────
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

        // ── Áp dụng giá trị mới ────────────────────────────────────────────
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

        // ── Cập nhật ví MỚI (hỗ trợ đổi ví khi chỉnh sửa giao dịch) ────────
        Account newAccount = (req.getAccountId() != null)
                ? accountRepository.findById(req.getAccountId())
                        .orElseThrow(() -> new ResourceNotFoundException("Account", "id", req.getAccountId()))
                : oldAccount;
        tx.setAccount(newAccount);

        // ── Áp dụng số dư cho ví mới ──────────────────────────────────────
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
        // ─── Hoàn nguyên số dư account ─────────────────────────────────────
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

    // =========================================================================
    //  API ĐỒNG BỘ BATCH — POST /api/transactions/sync
    //  Last-Write-Wins (LWW): PATH DELETE → PATH A (edit) → PATH B (new).
    // =========================================================================

    @Transactional(rollbackFor = Exception.class)
    public SyncResponse syncTransactions(SyncRequest req) {
        User user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", req.getUserId()));

        List<TransactionResponse> responseList = new ArrayList<>();
        int savedCount = 0;
        int skippedCount = 0;

        for (SyncTransactionRequest item : req.getTransactions()) {

            // ── PATH DELETE: soft-delete signal từ client ─────────────────────
            if (item.isDeleted() && item.getTransactionId() != null) {
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
                // BẮT BUỘC ACK — nếu không có, Android không nhận ack → zombie spam
                TransactionResponse ack = new TransactionResponse();
                ack.setId(item.getTransactionId());
                ack.setDeleted(true);
                responseList.add(ack);
                continue;
            }

            // ── PATH A: edit giao dịch đã tồn tại trên server ────────────────
            if (item.getTransactionId() != null) {
                java.util.Optional<Transaction> existingOpt =
                        transactionRepository.findByIdAndUser_Id(item.getTransactionId(), user.getId());
                if (existingOpt.isPresent()) {
                    Transaction existing = existingOpt.get();
                    // Null-safe LWW: clientUpdatedAt=null → Server Wins (app cũ)
                    // existing.updatedAt=null (legacy record) → Client Wins
                    boolean clientWins = item.getClientUpdatedAt() != null
                            && (existing.getUpdatedAt() == null
                                || item.getClientUpdatedAt().isAfter(existing.getUpdatedAt()));
                    if (!clientWins) {
                        // Server Wins: trả về version server để Android thoát Sync Blackhole
                        responseList.add(toResponse(existing));
                        continue;
                    }
                    // Client Wins: áp dụng edits với balance rollback
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
                    // Dùng timestamp của client làm ground truth — tắt @PreUpdate auto-stamp
                    existing.setUpdatedAt(item.getClientUpdatedAt());
                    existing.setClientSync(true);
                    transactionRepository.save(existing);
                    responseList.add(toResponse(existing));
                    savedCount++;
                    continue;
                }
                // Không tìm thấy trên server → fall through PATH B với UUID này
            }

            // ── PATH B: giao dịch mới ─────────────────────────────────────────
            if (item.getAccountId() == null || item.getCategoryId() == null || item.getAmount() == null) {
                skippedCount++;
                continue;
            }
            Account account = accountRepository.findById(item.getAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Account", "id", item.getAccountId()));
            Category category = categoryRepository.findById(item.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", item.getCategoryId()));

            if (item.getTransactionId() != null) {
                // Client UUID đã có nhưng không tìm thấy ở PATH A → INSERT với UUID đó
                // Không check duplicate (trust client UUID, tỷ lệ collision = 0%)
                Transaction newTx = buildNewTransaction(user, account, category, item);
                newTx.setId(item.getTransactionId());
                applyBalanceEffect(account, item.getType(), item.getAmount().abs());
                accountRepository.save(account);
                transactionRepository.save(newTx);
                responseList.add(toResponse(newTx));
                savedCount++;
                continue;
            }

            // Legacy (transactionId=null) — check duplicate để tránh double-send
            String categoryName = category.getName();
            java.util.Optional<Transaction> duplicate = transactionRepository.findDuplicate(
                    user.getId(), item.getAmount(), item.getTransactionDate(),
                    item.getType() != null ? item.getType().name() : null,
                    categoryName, item.getNote());
            if (duplicate.isPresent()) {
                // Trả về record đã tồn tại → Android đồng bộ UUID chuẩn, thoát loop
                responseList.add(toResponse(duplicate.get()));
                skippedCount++;
                continue;
            }

            Transaction newTx = buildNewTransaction(user, account, category, item);
            newTx.setId(UUID.randomUUID()); // server tự cấp UUID cho legacy records
            applyBalanceEffect(account, item.getType(), item.getAmount().abs());
            accountRepository.save(account);
            transactionRepository.save(newTx);
            responseList.add(toResponse(newTx));
            savedCount++;
        }

        return new SyncResponse(savedCount, skippedCount, responseList);
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

    // =========================================================================
    //  API TRUY XUẤT 30 NGÀY — GET /api/transactions/recent
    //  Trả về giao dịch trong 30 ngày qua, sắp xếp mới nhất lên đầu.
    // =========================================================================

    @Transactional(readOnly = true)
    public List<TransactionResponse> getRecentTransactions(UUID userId, int days) {
        // Dùng transactionDate (LocalDate) thay vì createdAt (OffsetDateTime)
        // để tránh Hibernate/Neon PostgreSQL timezone binding issue gây ra lỗi 500
        LocalDate since = LocalDate.now().minusDays(days);
        return transactionRepository
                .findByUser_IdAndTransactionDateGreaterThanEqualOrderByTransactionDateDescCreatedAtDesc(
                        userId, since)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getMyRecentTransactions(int days) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return getRecentTransactions(user.getId(), days);
    }

    // =========================================================================
    //  HELPER
    // =========================================================================

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

    // =========================================================================
    //  DELTA SYNC — GET /api/transactions/delta
    // =========================================================================

    @Transactional(readOnly = true)
    public DeltaResponse<TransactionResponse> getDelta(String updatedSince, String untilTimestamp,
                                                        String lastUpdatedAt, UUID lastId, int limit) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        OffsetDateTime since = updatedSince != null
                ? OffsetDateTime.parse(updatedSince)
                : OffsetDateTime.parse("1970-01-01T00:00:00Z");
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