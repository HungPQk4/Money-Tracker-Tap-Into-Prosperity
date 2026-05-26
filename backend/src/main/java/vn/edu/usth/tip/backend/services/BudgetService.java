package vn.edu.usth.tip.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.usth.tip.backend.dto.budget.BudgetResponse;
import vn.edu.usth.tip.backend.dto.budget.CreateBudgetRequest;
import vn.edu.usth.tip.backend.dto.common.DeltaResponse;
import vn.edu.usth.tip.backend.exception.ResourceNotFoundException;
import vn.edu.usth.tip.backend.models.Budget;
import vn.edu.usth.tip.backend.models.Category;
import vn.edu.usth.tip.backend.models.User;
import vn.edu.usth.tip.backend.repositories.BudgetRepository;
import vn.edu.usth.tip.backend.repositories.CategoryRepository;
import vn.edu.usth.tip.backend.repositories.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;

    public BudgetResponse createBudget(CreateBudgetRequest req) {
        User user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", req.getUserId()));
        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", req.getCategoryId()));
        Budget budget = new Budget();
        budget.setUser(user);
        budget.setCategory(category);
        budget.setAmount(req.getAmount());
        budget.setSpentAmount(req.getSpentAmount() != null ? req.getSpentAmount() : java.math.BigDecimal.ZERO);
        budget.setPeriodType(req.getPeriodType());
        budget.setPeriodStart(req.getPeriodStart());
        budget.setPeriodEnd(req.getPeriodEnd());
        budget.setAlertThreshold(req.getAlertThreshold() != null ? req.getAlertThreshold() : 80);
        return toResponse(budgetRepository.save(budget));
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> getBudgetsByUser(UUID userId) {
        return budgetRepository.findByUserId(userId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> getAllBudgets() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return getBudgetsByUser(user.getId());
    }

    public void deleteBudget(UUID id) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Budget", "id", id));
        OffsetDateTime now = OffsetDateTime.now();
        budget.setDeletedAt(now);
        budget.setUpdatedAt(now);
        budgetRepository.save(budget);
    }

    public BudgetResponse updateBudget(UUID id, CreateBudgetRequest req) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Budget", "id", id));
        
        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", req.getCategoryId()));
                
        budget.setCategory(category);
        budget.setAmount(req.getAmount());
        budget.setSpentAmount(req.getSpentAmount() != null ? req.getSpentAmount() : java.math.BigDecimal.ZERO);
        budget.setPeriodType(req.getPeriodType());
        budget.setPeriodStart(req.getPeriodStart());
        budget.setPeriodEnd(req.getPeriodEnd());
        budget.setAlertThreshold(req.getAlertThreshold() != null ? req.getAlertThreshold() : 80);
        
        return toResponse(budgetRepository.save(budget));
    }

    private BudgetResponse toResponse(Budget b) {
        BudgetResponse res = new BudgetResponse();
        res.setId(b.getId());
        res.setUserId(b.getUser().getId());
        res.setCategoryId(b.getCategory().getId());
        res.setCategoryName(b.getCategory().getName());
        res.setAmount(b.getAmount());
        res.setSpentAmount(b.getSpentAmount());
        res.setPeriodType(b.getPeriodType());
        res.setPeriodStart(b.getPeriodStart());
        res.setPeriodEnd(b.getPeriodEnd());
        res.setAlertThreshold(b.getAlertThreshold());
        res.setCreatedAt(b.getCreatedAt());
        res.setUpdatedAt(b.getUpdatedAt());
        res.setDeleted(b.getDeletedAt() != null);
        return res;
    }

    @Transactional(readOnly = true)
    public DeltaResponse<BudgetResponse> getDelta(String updatedSince, String untilTimestamp,
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
        Slice<Budget> slice = budgetRepository.findDelta(
                user.getId(), since, until, cursorTs, lastId, PageRequest.of(0, limit));
        return new DeltaResponse<>(
                slice.getContent().stream().map(this::toResponse).toList(),
                slice.hasNext(),
                until.toString());
    }
}
