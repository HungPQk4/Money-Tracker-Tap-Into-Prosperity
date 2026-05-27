package vn.edu.usth.tip.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.usth.tip.backend.dto.budget.BudgetResponse;
import vn.edu.usth.tip.backend.dto.budget.CreateBudgetRequest;
import vn.edu.usth.tip.backend.dto.common.DeltaResponse;
import vn.edu.usth.tip.backend.exception.ResourceNotFoundException;
import vn.edu.usth.tip.backend.models.Budget;
import vn.edu.usth.tip.backend.models.Category;
import vn.edu.usth.tip.backend.models.User;
import vn.edu.usth.tip.backend.repositories.BudgetRepository;
import vn.edu.usth.tip.backend.repositories.CategoryRepository;
import vn.edu.usth.tip.backend.utils.BudgetConstants;
import vn.edu.usth.tip.backend.utils.SecurityUtils;
import vn.edu.usth.tip.backend.utils.SyncConstants;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final SecurityUtils securityUtils;
    private final CategoryRepository categoryRepository;

    @Transactional
    public BudgetResponse createBudget(CreateBudgetRequest req) {
        User user = securityUtils.getCurrentUser();
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
        budget.setAlertThreshold(req.getAlertThreshold() != null ? req.getAlertThreshold() : BudgetConstants.ALERT_THRESHOLD_PERCENT);

        return toResponse(budgetRepository.save(budget));
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> getBudgetsByUser(UUID userId) {
        return budgetRepository.findByUserId(userId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> getAllBudgets() {
        return getBudgetsByUser(securityUtils.getCurrentUser().getId());
    }

    @Transactional
    public void deleteBudget(UUID id) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Budget", "id", id));
        if (!budget.getUser().getId().equals(securityUtils.getCurrentUser().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        OffsetDateTime now = OffsetDateTime.now();
        budget.setDeletedAt(now);
        budget.setUpdatedAt(now);
        budgetRepository.save(budget);
    }

    @Transactional
    public BudgetResponse updateBudget(UUID id, CreateBudgetRequest req) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Budget", "id", id));
        if (!budget.getUser().getId().equals(securityUtils.getCurrentUser().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }

        Category category = categoryRepository.findById(req.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", req.getCategoryId()));

        budget.setCategory(category);
        budget.setAmount(req.getAmount());
        budget.setSpentAmount(req.getSpentAmount() != null ? req.getSpentAmount() : java.math.BigDecimal.ZERO);
        budget.setPeriodType(req.getPeriodType());
        budget.setPeriodStart(req.getPeriodStart());
        budget.setPeriodEnd(req.getPeriodEnd());
        budget.setAlertThreshold(req.getAlertThreshold() != null ? req.getAlertThreshold() : BudgetConstants.ALERT_THRESHOLD_PERCENT);

        return toResponse(budgetRepository.save(budget));
    }

    private BudgetResponse toResponse(Budget budget) {
        BudgetResponse res = new BudgetResponse();
        res.setId(budget.getId());
        res.setUserId(budget.getUser().getId());
        res.setCategoryId(budget.getCategory().getId());
        res.setCategoryName(budget.getCategory().getName());
        res.setAmount(budget.getAmount());
        res.setSpentAmount(budget.getSpentAmount());
        res.setPeriodType(budget.getPeriodType());
        res.setPeriodStart(budget.getPeriodStart());
        res.setPeriodEnd(budget.getPeriodEnd());
        res.setAlertThreshold(budget.getAlertThreshold());
        res.setCreatedAt(budget.getCreatedAt());
        res.setUpdatedAt(budget.getUpdatedAt());
        res.setDeleted(budget.getDeletedAt() != null);
        return res;
    }

    @Transactional(readOnly = true)
    public DeltaResponse<BudgetResponse> getDelta(String updatedSince, String untilTimestamp,
                                                   String lastUpdatedAt, UUID lastId, int limit) {
        User user = securityUtils.getCurrentUser();
        OffsetDateTime since = updatedSince != null
                ? OffsetDateTime.parse(updatedSince)
                : OffsetDateTime.parse(SyncConstants.EPOCH_CURSOR);
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
