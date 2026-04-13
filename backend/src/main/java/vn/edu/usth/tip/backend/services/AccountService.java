package vn.edu.usth.tip.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.edu.usth.tip.backend.dto.account.AccountResponse;
import vn.edu.usth.tip.backend.dto.account.CreateAccountRequest;
import vn.edu.usth.tip.backend.exception.ResourceNotFoundException;
import vn.edu.usth.tip.backend.models.Account;
import vn.edu.usth.tip.backend.models.User;
import vn.edu.usth.tip.backend.repositories.AccountRepository;
import vn.edu.usth.tip.backend.repositories.UserRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountResponse createAccount(CreateAccountRequest req) {
        User user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", req.getUserId()));
        Account account = new Account();
        account.setUser(user);
        account.setName(req.getName());
        account.setType(req.getType());
        account.setBalance(req.getBalance());
        account.setCurrencyCode(req.getCurrencyCode());
        account.setIsDefault(req.getIsDefault());
        return toResponse(accountRepository.save(account));
    }

    public List<AccountResponse> getAccountsByUser(UUID userId) {
        return accountRepository.findByUserId(userId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public AccountResponse getAccountById(UUID id) {
        return toResponse(accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id)));
    }

    public AccountResponse updateAccount(UUID id, CreateAccountRequest req) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id));
        account.setName(req.getName());
        account.setType(req.getType());
        account.setBalance(req.getBalance());
        account.setCurrencyCode(req.getCurrencyCode());
        account.setIsDefault(req.getIsDefault());
        return toResponse(accountRepository.save(account));
    }

    public void deleteAccount(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account", "id", id));
        accountRepository.delete(account);
    }

    private AccountResponse toResponse(Account a) {
        AccountResponse res = new AccountResponse();
        res.setId(a.getId());
        res.setUserId(a.getUser().getId());
        res.setName(a.getName());
        res.setType(a.getType());
        res.setBalance(a.getBalance());
        res.setCurrencyCode(a.getCurrencyCode());
        res.setIsDefault(a.getIsDefault());
        res.setIsActive(a.getIsActive());
        res.setCreatedAt(a.getCreatedAt());
        return res;
    }
}
