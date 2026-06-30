package vn.edu.usth.tip.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.usth.tip.backend.dto.account.AccountRequest;
import vn.edu.usth.tip.backend.dto.account.AccountResponse;
import vn.edu.usth.tip.backend.dto.common.DeltaResponse;
import vn.edu.usth.tip.backend.exception.ResourceNotFoundException;
import vn.edu.usth.tip.backend.models.Account;
import vn.edu.usth.tip.backend.models.User;
import vn.edu.usth.tip.backend.repositories.AccountRepository;
import vn.edu.usth.tip.backend.repositories.TransactionRepository;
import vn.edu.usth.tip.backend.utils.SecurityUtils;
import vn.edu.usth.tip.backend.utils.SyncConstants;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final SecurityUtils securityUtils;

    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        User user = securityUtils.getCurrentUser();
        List<Account> accounts = accountRepository.findByUserId(user.getId());
        return accounts.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public AccountResponse createAccount(AccountRequest request) {
        User user = securityUtils.getCurrentUser();
        Account account = new Account();
        account.setUser(user);
        account.setName(request.getName());
        account.setType(request.getType());
        // Số dư nhập lúc lập ví = số dư đầu kỳ; chưa có giao dịch nên balance = openingBalance.
        BigDecimal opening = request.getBalance() != null ? request.getBalance() : BigDecimal.ZERO;
        account.setOpeningBalance(opening);
        account.setBalance(opening);
        account.setColorHex(request.getColorHex());
        account.setIcon(request.getIcon());
        account.setIncludeInTotal(request.getIncludeInTotal() != null ? request.getIncludeInTotal() : true);
        account.setIsDefault(false);
        account.setIsActive(true);
        
        Account savedAccount = accountRepository.save(account);
        return toResponse(savedAccount);
    }

    @Transactional
    public AccountResponse updateAccount(java.util.UUID id, AccountRequest request) {
        User user = securityUtils.getCurrentUser();
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id.toString()));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Bạn không có quyền chỉnh sửa ví này");
        }

        account.setName(request.getName());
        if (request.getType() != null) {
            account.setType(request.getType());
        }
        if (request.getBalance() != null) {
            // Người dùng nhập số dư MONG MUỐN (tổng) → suy ngược số dư đầu kỳ, giữ nguyên giao dịch:
            //   openingBalance = số_dư_nhập − Σ giao dịch  ⇒  balance hiển thị = số_dư_nhập.
            BigDecimal txSum = transactionRepository.computeBalanceForAccount(id);
            if (txSum == null) txSum = BigDecimal.ZERO;
            account.setOpeningBalance(request.getBalance().subtract(txSum));
            account.setBalance(request.getBalance());
        }
        if (request.getColorHex() != null) {
            account.setColorHex(request.getColorHex());
        }
        if (request.getIcon() != null) {
            account.setIcon(request.getIcon());
        }
        if (request.getIncludeInTotal() != null) {
            account.setIncludeInTotal(request.getIncludeInTotal());
        }

        Account updatedAccount = accountRepository.save(account);
        return toResponse(updatedAccount);
    }

    @Transactional
    public void deleteAccount(java.util.UUID id) {
        User user = securityUtils.getCurrentUser();
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id.toString()));

        if (!account.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Bạn không có quyền xóa ví này");
        }

        OffsetDateTime now = OffsetDateTime.now();
        account.setDeletedAt(now);
        account.setUpdatedAt(now);
        accountRepository.save(account);
    }

    private AccountResponse toResponse(Account acc) {
        AccountResponse res = new AccountResponse();
        res.setId(acc.getId());
        res.setName(acc.getName());
        res.setType(acc.getType());
        res.setBalance(acc.getBalance());
        res.setOpeningBalance(acc.getOpeningBalance());
        res.setCurrencyCode(acc.getCurrencyCode());
        res.setColorHex(acc.getColorHex());
        res.setIcon(acc.getIcon());
        res.setIncludeInTotal(acc.getIncludeInTotal());
        res.setIsDefault(acc.getIsDefault());
        res.setIsActive(acc.getIsActive());
        res.setUpdatedAt(acc.getUpdatedAt());
        res.setDeleted(acc.getDeletedAt() != null);
        return res;
    }

    @Transactional(readOnly = true)
    public DeltaResponse<AccountResponse> getDelta(String updatedSince, String untilTimestamp,
                                                    String lastUpdatedAt, UUID lastId, int limit) {
        User user = securityUtils.getCurrentUser();
        OffsetDateTime since = updatedSince != null
                ? OffsetDateTime.parse(updatedSince)
                : OffsetDateTime.parse(SyncConstants.EPOCH_CURSOR);
        OffsetDateTime until = untilTimestamp != null
                ? OffsetDateTime.parse(untilTimestamp)
                : OffsetDateTime.now();
        OffsetDateTime cursorTs = lastUpdatedAt != null ? OffsetDateTime.parse(lastUpdatedAt) : null;
        Slice<Account> slice = accountRepository.findDelta(
                user.getId(), since, until, cursorTs, lastId, PageRequest.of(0, limit));
        return new DeltaResponse<>(
                slice.getContent().stream().map(this::toResponse).toList(),
                slice.hasNext(),
                until.toString());
    }

    // ─── Reconciliation: tính lại số dư từ giao dịch (chống trôi lệch đa thiết bị) ──

    /**
     * Tính lại số dư một ví = Σ(thu) − Σ(chi + chuyển) từ các giao dịch chưa xóa.
     * Số dư là counter cộng dồn nên không an toàn dưới LWW khi nhiều thiết bị cùng sửa;
     * gọi hàm này để đưa số dư về đúng tổng giao dịch.
     */
    @Transactional
    public AccountResponse reconcileBalance(UUID accountId) {
        User user = securityUtils.getCurrentUser();
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", accountId.toString()));
        if (!account.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Bạn không có quyền truy cập ví này");
        }
        applyReconciledBalance(account);
        return toResponse(account);
    }

    /** Tính lại số dư cho TẤT CẢ ví của user hiện tại. */
    @Transactional
    public List<AccountResponse> reconcileAllBalances() {
        User user = securityUtils.getCurrentUser();
        List<AccountResponse> result = new ArrayList<>();
        for (Account account : accountRepository.findByUserId(user.getId())) {
            applyReconciledBalance(account);
            result.add(toResponse(account));
        }
        return result;
    }

    private void applyReconciledBalance(Account account) {
        BigDecimal computed = transactionRepository.computeBalanceForAccount(account.getId());
        if (computed == null) computed = BigDecimal.ZERO;
        // Số dư = số dư đầu kỳ + Σ giao dịch (đầu kỳ null = 0 cho dòng cũ).
        BigDecimal opening = account.getOpeningBalance() != null ? account.getOpeningBalance() : BigDecimal.ZERO;
        computed = opening.add(computed);
        // Chỉ ghi khi lệch → tránh bump updatedAt thừa (đỡ tạo delta vô ích).
        if (account.getBalance() == null || account.getBalance().compareTo(computed) != 0) {
            account.setBalance(computed);
            accountRepository.save(account); // @PreUpdate bump updatedAt → đẩy số dư đúng về client qua delta
        }
    }
}
