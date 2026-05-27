package vn.edu.usth.tip.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.usth.tip.backend.dto.common.DeltaResponse;
import vn.edu.usth.tip.backend.dto.goal.CreateGoalRequest;
import vn.edu.usth.tip.backend.dto.goal.GoalResponse;
import vn.edu.usth.tip.backend.exception.ResourceNotFoundException;
import vn.edu.usth.tip.backend.models.Goal;
import vn.edu.usth.tip.backend.models.User;
import vn.edu.usth.tip.backend.models.enums.GoalStatus;
import vn.edu.usth.tip.backend.repositories.GoalRepository;
import vn.edu.usth.tip.backend.utils.SecurityUtils;
import vn.edu.usth.tip.backend.utils.SyncConstants;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;
    private final SecurityUtils securityUtils;

    @Transactional
    public GoalResponse createGoal(CreateGoalRequest req) {
        User user = securityUtils.getCurrentUser();
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
        return getGoalsByUser(securityUtils.getCurrentUser().getId());
    }

    @Transactional
    public GoalResponse updateProgress(UUID id, BigDecimal amount) {
        Goal goal = goalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goal", "id", id));
        if (!goal.getUser().getId().equals(securityUtils.getCurrentUser().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        goal.setCurrentAmount(goal.getCurrentAmount().add(amount));
        if (goal.getCurrentAmount().compareTo(goal.getTargetAmount()) >= 0) {
            goal.setStatus(GoalStatus.completed);
        }
        return toResponse(goalRepository.save(goal));
    }

    @Transactional
    public void deleteGoal(UUID id) {
        Goal goal = goalRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Goal", "id", id));
        if (!goal.getUser().getId().equals(securityUtils.getCurrentUser().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        OffsetDateTime now = OffsetDateTime.now();
        goal.setDeletedAt(now);
        goal.setUpdatedAt(now);
        goalRepository.save(goal);
    }

    private GoalResponse toResponse(Goal goal) {
        GoalResponse res = new GoalResponse();
        res.setId(goal.getId());
        res.setUserId(goal.getUser().getId());
        res.setName(goal.getName());
        res.setTargetAmount(goal.getTargetAmount());
        res.setCurrentAmount(goal.getCurrentAmount());
        res.setDeadline(goal.getDeadline());
        res.setStatus(goal.getStatus());
        res.setIcon(goal.getIcon());
        res.setColorHex(goal.getColorHex());
        res.setCreatedAt(goal.getCreatedAt());
        res.setUpdatedAt(goal.getUpdatedAt());
        res.setDeleted(goal.getDeletedAt() != null);
        return res;
    }

    @Transactional(readOnly = true)
    public DeltaResponse<GoalResponse> getDelta(String updatedSince, String untilTimestamp,
                                                 String lastUpdatedAt, UUID lastId, int limit) {
        User user = securityUtils.getCurrentUser();
        OffsetDateTime since = updatedSince != null
                ? OffsetDateTime.parse(updatedSince)
                : OffsetDateTime.parse(SyncConstants.EPOCH_CURSOR);
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
