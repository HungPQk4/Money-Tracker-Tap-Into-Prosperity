package vn.edu.usth.tip.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.usth.tip.backend.dto.common.DeltaResponse;
import vn.edu.usth.tip.backend.dto.goal.CreateGoalRequest;
import vn.edu.usth.tip.backend.dto.goal.GoalResponse;
import vn.edu.usth.tip.backend.exception.ResourceNotFoundException;
import vn.edu.usth.tip.backend.models.Goal;
import vn.edu.usth.tip.backend.models.User;
import vn.edu.usth.tip.backend.models.enums.GoalStatus;
import vn.edu.usth.tip.backend.repositories.GoalRepository;
import vn.edu.usth.tip.backend.repositories.UserRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
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

    @Transactional(readOnly = true)
    public List<GoalResponse> getGoalsByUser(UUID userId) {
        return goalRepository.findByUserId(userId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> getAllGoals() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return getGoalsByUser(user.getId());
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
        OffsetDateTime now = OffsetDateTime.now();
        goal.setDeletedAt(now);
        goal.setUpdatedAt(now);
        goalRepository.save(goal);
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
        res.setUpdatedAt(g.getUpdatedAt());
        res.setDeleted(g.getDeletedAt() != null);
        return res;
    }

    @Transactional(readOnly = true)
    public DeltaResponse<GoalResponse> getDelta(String updatedSince, String untilTimestamp,
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
        Slice<Goal> slice = goalRepository.findDelta(
                user.getId(), since, until, cursorTs, lastId, PageRequest.of(0, limit));
        return new DeltaResponse<>(
                slice.getContent().stream().map(this::toResponse).toList(),
                slice.hasNext(),
                until.toString());
    }
}
