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

        // ── Hoàn nguyên số dư cũ trước khi update ──────────────────────────
        Account account = tx.getAccount();
        if (account != null && tx.getAmount() != null) {
            BigDecimal current = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
            if (TransactionType.income == tx.getType()) {
                account.setBalance(current.subtract(tx.getAmount()));
            } else if (TransactionType.expense == tx.getType() || TransactionType.transfer == tx.getType()) {
                account.setBalance(current.add(tx.getAmount()));
            }
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

        // ── Cập nhật số dư mới ─────────────────────────────────────────────
        if (account != null) {
            BigDecimal current = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
            if (TransactionType.income == req.getType()) {
                account.setBalance(current.add(req.getAmount()));
            } else if (TransactionType.expense == req.getType() || TransactionType.transfer == req.getType()) {
                account.setBalance(current.subtract(req.getAmount()));
            }
            accountRepository.save(account);
        }

        return toResponse(transactionRepository.save(tx));
    }

    @Transactional
    public void deleteTransaction(UUID id) {
        Transaction tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));
        // ─── Hoàn nguyên số dư account ─────────────────────────────────────
        Account account = tx.getAccount();
        if (account != null && tx.getAmount() != null) {
            BigDecimal currentBalance = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
            if (TransactionType.income == tx.getType()) {
                account.setBalance(currentBalance.subtract(tx.getAmount()));
            } else if (TransactionType.expense == tx.getType() || TransactionType.transfer == tx.getType()) {
                account.setBalance(currentBalance.add(tx.getAmount()));
            }
            accountRepository.save(account);
        }
        transactionRepository.delete(tx);
    }

    // =========================================================================
    //  API ĐỒNG BỘ BATCH — POST /api/transactions/sync
    //  Nhận list giao dịch offline, lưu vào DB giữ nguyên createdAt gốc.
    // =========================================================================

    @Transactional
    public SyncResponse syncTransactions(SyncRequest req) {
        User user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", req.getUserId()));

        List<Transaction> toSave = new ArrayList<>();
        int skipped = 0;

        for (SyncTransactionRequest item : req.getTransactions()) {
            // ── Lấy thời điểm createdAt: ưu tiên giá trị client gửi lên ─────
            OffsetDateTime clientCreatedAt = item.getCreatedAt() != null
                    ? item.getCreatedAt()
                    : OffsetDateTime.now();

            // ── Kiểm tra trùng lặp trước khi lưu ────────────────────────────
            // Dùng native query → truyền type dưới dạng String (lowercase)
            boolean duplicate = transactionRepository.existsDuplicate(
                    user.getId(),
                    item.getAmount(),
                    item.getTransactionDate(),
                    item.getType() != null ? item.getType().name() : null
            );
            if (duplicate) {
                skipped++;
                continue;
            }

            Account account = accountRepository.findById(item.getAccountId())
                    .orElseThrow(() -> new ResourceNotFoundException("Account", "id", item.getAccountId()));
            Category category = categoryRepository.findById(item.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", item.getCategoryId()));

            Transaction tx = new Transaction();
            tx.setUser(user);
            tx.setAccount(account);
            tx.setCategory(category);
            if (item.getGoalId() != null) {
                tx.setGoal(goalRepository.findById(item.getGoalId())
                        .orElseThrow(() -> new ResourceNotFoundException("Goal", "id", item.getGoalId())));
            }

            // ── Normalize: amount LUÔN dương — type xác định thu/chi ──────────
            // Android có thể gửi số âm cho expense → server tự abs() để tránh
            // vi phạm DB constraint CHECK(amount > 0)
            BigDecimal positiveAmount = item.getAmount().abs();

            tx.setAmount(positiveAmount);
            tx.setType(item.getType());
            tx.setNote(item.getNote());
            tx.setTransactionDate(item.getTransactionDate());
            tx.setReceiptUrl(item.getReceiptUrl());
            tx.setIsRecurring(item.getIsRecurring() != null ? item.getIsRecurring() : false);
            tx.setRecurInterval(item.getRecurInterval());

            // ── Gán createdAt của client — @PrePersist sẽ không ghi đè ───────
            tx.setCreatedAt(clientCreatedAt);
            tx.setUpdatedAt(OffsetDateTime.now());

            // ── Cập nhật số dư account ────────────────────────────────────────
            BigDecimal bal = account.getBalance() != null ? account.getBalance() : BigDecimal.ZERO;
            if (TransactionType.income == item.getType()) {
                account.setBalance(bal.add(positiveAmount));
            } else if (TransactionType.expense == item.getType() || TransactionType.transfer == item.getType()) {
                account.setBalance(bal.subtract(positiveAmount));
            }
            accountRepository.save(account);

            toSave.add(tx);

        }

        List<Transaction> saved = transactionRepository.saveAll(toSave);
        List<TransactionResponse> responses = saved.stream().map(this::toResponse).collect(Collectors.toList());

        return new SyncResponse(saved.size(), skipped, responses);
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
        return res;
    }
}