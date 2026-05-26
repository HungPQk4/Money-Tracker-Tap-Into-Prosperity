package vn.edu.usth.tip.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.usth.tip.backend.dto.account.AccountRequest;
import vn.edu.usth.tip.backend.dto.account.AccountResponse;
import vn.edu.usth.tip.backend.dto.common.DeltaResponse;
import vn.edu.usth.tip.backend.exception.ResourceNotFoundException;
import vn.edu.usth.tip.backend.models.Account;
import vn.edu.usth.tip.backend.models.User;
import vn.edu.usth.tip.backend.repositories.AccountRepository;
import vn.edu.usth.tip.backend.repositories.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        User user = getCurrentUser();
        List<Account> accounts = accountRepository.findByUserId(user.getId());
        return accounts.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional
    public AccountResponse createAccount(AccountRequest request) {
        User user = getCurrentUser();
        Account account = new Account();
        account.setUser(user);
        account.setName(request.getName());
        account.setType(request.getType());
        account.setBalance(request.getBalance());
        account.setColorHex(request.getColorHex());
        account.setIcon(request.getIcon());
        account.setIncludeInTotal(request.getIncludeInTotal() != null ? request.getIncludeInTotal() : true);
        account.setIsDefault(false);
        account.setIsActive(true);
        
        Account savedAccount = accountRepository.save(account);
        return mapToResponse(savedAccount);
    }

    @Transactional
    public AccountResponse updateAccount(java.util.UUID id, AccountRequest request) {
        User user = getCurrentUser();
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
        return mapToResponse(updatedAccount);
    }

    @Transactional
    public void deleteAccount(java.util.UUID id) {
        User user = getCurrentUser();
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

    private AccountResponse mapToResponse(Account account) {
        AccountResponse response = new AccountResponse();
        response.setId(account.getId());
        response.setName(account.getName());
        response.setType(account.getType());
        response.setBalance(account.getBalance());
        response.setCurrencyCode(account.getCurrencyCode());
        response.setColorHex(account.getColorHex());
        response.setIcon(account.getIcon());
        response.setIncludeInTotal(account.getIncludeInTotal());
        response.setIsDefault(account.getIsDefault());
        response.setIsActive(account.getIsActive());
        response.setUpdatedAt(account.getUpdatedAt());
        response.setDeleted(account.getDeletedAt() != null);
        return response;
    }

    @Transactional(readOnly = true)
    public DeltaResponse<AccountResponse> getDelta(String updatedSince, String untilTimestamp,
                                                    String lastUpdatedAt, UUID lastId, int limit) {
        User user = getCurrentUser();
        OffsetDateTime since = updatedSince != null
                ? OffsetDateTime.parse(updatedSince)
                : OffsetDateTime.parse("1970-01-01T00:00:00Z");
        OffsetDateTime until = untilTimestamp != null
                ? OffsetDateTime.parse(untilTimestamp)
                : OffsetDateTime.now();
        OffsetDateTime cursorTs = lastUpdatedAt != null ? OffsetDateTime.parse(lastUpdatedAt) : null;
        Slice<Account> slice = accountRepository.findDelta(
                user.getId(), since, until, cursorTs, lastId, PageRequest.of(0, limit));
        return new DeltaResponse<>(
                slice.getContent().stream().map(this::mapToResponse).toList(),
                slice.hasNext(),
                until.toString());
    }
}
