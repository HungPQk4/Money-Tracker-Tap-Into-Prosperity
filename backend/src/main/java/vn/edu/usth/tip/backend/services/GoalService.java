package vn.edu.usth.tip.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.edu.usth.tip.backend.dto.goal.CreateGoalRequest;
import vn.edu.usth.tip.backend.dto.goal.GoalResponse;
import vn.edu.usth.tip.backend.exception.ResourceNotFoundException;
import vn.edu.usth.tip.backend.models.Goal;
import vn.edu.usth.tip.backend.models.User;
import vn.edu.usth.tip.backend.models.enums.GoalStatus;
import vn.edu.usth.tip.backend.repositories.GoalRepository;
import vn.edu.usth.tip.backend.repositories.UserRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;
    private final UserRepository userRepository;

    public GoalResponse createGoal(CreateGoalRequest req) {
        User user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", req.getUserId()));
        Goal goal = new Goal();
        goal.setUser(user);
        goal.setName(req.getName());
        goal.setTargetAmount(req.getTargetAmount());
        goal.setCurrentAmount(req.getCurrentAmount() != null ? req.getCurrentAmount() : BigDecimal.ZERO);
        goal.setDeadline(req.getDeadline());
        goal.setIcon(req.getIcon());
        goal.setColorHex(req.getColorHex());
        goal.setStatus(GoalStatus.active);
        return toResponse(goalRepository.save(goal));
    }

    public List<GoalResponse> getGoalsByUser(UUID userId) {
        return goalRepository.findByUserId(userId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public GoalResponse updateProgress(UUID id, BigDecimal amount) {
        Goal goal = goalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goal", "id", id));
        goal.setCurrentAmount(goal.getCurrentAmount().add(amount));
        if (goal.getCurrentAmount().compareTo(goal.getTargetAmount()) >= 0) {
            goal.setStatus(GoalStatus.completed);
        }
        return toResponse(goalRepository.save(goal));
    }

    public void deleteGoal(UUID id) {
        Goal goal = goalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goal", "id", id));
        goalRepository.delete(goal);
    }

    private GoalResponse toResponse(Goal g) {
        GoalResponse res = new GoalResponse();
        res.setId(g.getId());
        res.setUserId(g.getUser().getId());
        res.setName(g.getName());
        res.setTargetAmount(g.getTargetAmount());
        res.setCurrentAmount(g.getCurrentAmount());
        res.setDeadline(g.getDeadline());
        res.setStatus(g.getStatus());
        res.setIcon(g.getIcon());
        res.setColorHex(g.getColorHex());
        res.setCreatedAt(g.getCreatedAt());
        return res;
    }
}
