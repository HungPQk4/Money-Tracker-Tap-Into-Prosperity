package vn.edu.usth.tip.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.usth.tip.backend.dto.dashboard.DashboardSummaryDto;
import vn.edu.usth.tip.backend.dto.transaction.TransactionResponse;
import vn.edu.usth.tip.backend.exception.ResourceNotFoundException;
import vn.edu.usth.tip.backend.models.Transaction;
import vn.edu.usth.tip.backend.models.User;
import vn.edu.usth.tip.backend.models.enums.TransactionType;
import vn.edu.usth.tip.backend.repositories.AccountRepository;
import vn.edu.usth.tip.backend.repositories.TransactionRepository;
import vn.edu.usth.tip.backend.repositories.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
    }

    @Transactional(readOnly = true)
    public DashboardSummaryDto getDashboardSummary() {
        User user = getCurrentUser();

        BigDecimal netWorth = accountRepository.getTotalBalanceByUserId(user.getId());

        LocalDate now = LocalDate.now();
        LocalDate startOfMonth = now.withDayOfMonth(1);
        LocalDate endOfMonth = now.withDayOfMonth(now.lengthOfMonth());

        BigDecimal totalIncome = transactionRepository.getTotalAmountByTypeAndDateRange(
                user.getId(), TransactionType.income, startOfMonth, endOfMonth);
        
        BigDecimal totalExpense = transactionRepository.getTotalAmountByTypeAndDateRange(
                user.getId(), TransactionType.expense, startOfMonth, endOfMonth);

        BigDecimal totalTransfer = transactionRepository.getTotalAmountByTypeAndDateRange(
                user.getId(), TransactionType.transfer, startOfMonth, endOfMonth);

        return new DashboardSummaryDto(netWorth, totalIncome, totalExpense, totalTransfer);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getRecentTransactions(String period) {
        User user = getCurrentUser();
        LocalDate now = LocalDate.now();
        LocalDate startDate;

        if ("today".equalsIgnoreCase(period)) {
            startDate = now;
        } else if ("week".equalsIgnoreCase(period)) {
            startDate = now.minusDays(now.getDayOfWeek().getValue() - 1);
        } else {
            // default to month
            startDate = now.withDayOfMonth(1);
        }

        List<Transaction> transactions = transactionRepository
                .findByUser_IdAndTransactionDateBetweenOrderByTransactionDateDesc(user.getId(), startDate, now);

        return transactions.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    private TransactionResponse mapToResponse(Transaction tx) {
        TransactionResponse dto = new TransactionResponse();
        dto.setId(tx.getId());
        dto.setUserId(tx.getUser().getId());
        dto.setAccountId(tx.getAccount().getId());
        dto.setAccountName(tx.getAccount().getName());
        dto.setCategoryId(tx.getCategory().getId());
        dto.setCategoryName(tx.getCategory().getName());
        dto.setGoalId(tx.getGoal() != null ? tx.getGoal().getId() : null);
        dto.setAmount(tx.getAmount());
        dto.setType(tx.getType());
        dto.setNote(tx.getNote());
        dto.setTransactionDate(tx.getTransactionDate());
        dto.setReceiptUrl(tx.getReceiptUrl());
        dto.setIsRecurring(tx.getIsRecurring());
        dto.setRecurInterval(tx.getRecurInterval());
        dto.setCreatedAt(tx.getCreatedAt());
        return dto;
    }
}
