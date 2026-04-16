package vn.edu.usth.tip.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.usth.tip.backend.dto.account.AccountRequest;
import vn.edu.usth.tip.backend.dto.account.AccountResponse;
import vn.edu.usth.tip.backend.exception.ResourceNotFoundException;
import vn.edu.usth.tip.backend.models.Account;
import vn.edu.usth.tip.backend.models.User;
import vn.edu.usth.tip.backend.repositories.AccountRepository;
import vn.edu.usth.tip.backend.repositories.UserRepository;

import java.util.List;
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
        
        Account savedAccount = accountRepository.save(account);
        return mapToResponse(savedAccount);
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
        return response;
    }
}
