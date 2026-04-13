package vn.edu.usth.tip.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.edu.usth.tip.backend.dto.budget.BudgetResponse;
import vn.edu.usth.tip.backend.dto.budget.CreateBudgetRequest;
import vn.edu.usth.tip.backend.exception.ResourceNotFoundException;
import vn.edu.usth.tip.backend.models.Budget;
import vn.edu.usth.tip.backend.models.Category;
import vn.edu.usth.tip.backend.models.User;
import vn.edu.usth.tip.backend.repositories.BudgetRepository;
import vn.edu.usth.tip.backend.repositories.CategoryRepository;
import vn.edu.usth.tip.backend.repositories.UserRepository;

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
        budget.setPeriodType(req.getPeriodType());
        budget.setPeriodStart(req.getPeriodStart());
        budget.setPeriodEnd(req.getPeriodEnd());
        budget.setAlertThreshold(req.getAlertThreshold() != null ? req.getAlertThreshold() : 80);
        return toResponse(budgetRepository.save(budget));
    }

    public List<BudgetResponse> getBudgetsByUser(UUID userId) {
        return budgetRepository.findByUserId(userId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public void deleteBudget(UUID id) {
        Budget budget = budgetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Budget", "id", id));
        budgetRepository.delete(budget);
    }

    private BudgetResponse toResponse(Budget b) {
        BudgetResponse res = new BudgetResponse();
        res.setId(b.getId());
        res.setUserId(b.getUser().getId());
        res.setCategoryId(b.getCategory().getId());
        res.setCategoryName(b.getCategory().getName());
        res.setAmount(b.getAmount());
        res.setPeriodType(b.getPeriodType());
        res.setPeriodStart(b.getPeriodStart());
        res.setPeriodEnd(b.getPeriodEnd());
        res.setAlertThreshold(b.getAlertThreshold());
        res.setCreatedAt(b.getCreatedAt());
        return res;
    }
}
