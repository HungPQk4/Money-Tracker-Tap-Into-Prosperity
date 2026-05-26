package vn.edu.usth.tip.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.usth.tip.backend.dto.common.DeltaResponse;
import vn.edu.usth.tip.backend.dto.debt.CreateDebtRequest;
import vn.edu.usth.tip.backend.dto.debt.DebtResponse;
import vn.edu.usth.tip.backend.exception.ResourceNotFoundException;
import vn.edu.usth.tip.backend.models.Debt;
import vn.edu.usth.tip.backend.models.User;
import vn.edu.usth.tip.backend.models.enums.DebtStatus;
import vn.edu.usth.tip.backend.repositories.DebtRepository;
import vn.edu.usth.tip.backend.repositories.UserRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DebtService {

    private final DebtRepository debtRepository;
    private final UserRepository userRepository;

    public DebtResponse createDebt(CreateDebtRequest req) {
        User user = userRepository.findById(req.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", req.getUserId()));
        Debt debt = new Debt();
        debt.setUser(user);
        debt.setContactName(req.getContactName());
        debt.setContactPhone(req.getContactPhone());
        debt.setType(req.getType());
        debt.setAmount(req.getAmount());
        debt.setRemainingAmount(req.getAmount()); // initially the full amount
        debt.setDueDate(req.getDueDate());
        debt.setNote(req.getNote());
        debt.setStatus(DebtStatus.active);
        return toResponse(debtRepository.save(debt));
    }

    @Transactional(readOnly = true)
    public List<DebtResponse> getDebtsByUser(UUID userId) {
        return debtRepository.findByUserId(userId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<DebtResponse> getAllDebts() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return getDebtsByUser(user.getId());
    }

    public DebtResponse settleDebt(UUID id) {
        Debt debt = debtRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Debt", "id", id));
        debt.setRemainingAmount(BigDecimal.ZERO);
        debt.setStatus(DebtStatus.settled);
        return toResponse(debtRepository.save(debt));
    }

    public void deleteDebt(UUID id) {
        Debt debt = debtRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Debt", "id", id));
        OffsetDateTime now = OffsetDateTime.now();
        debt.setDeletedAt(now);
        debt.setUpdatedAt(now);
        debtRepository.save(debt);
    }

    private DebtResponse toResponse(Debt d) {
        DebtResponse res = new DebtResponse();
        res.setId(d.getId());
        res.setUserId(d.getUser().getId());
        res.setContactName(d.getContactName());
        res.setContactPhone(d.getContactPhone());
        res.setType(d.getType());
        res.setAmount(d.getAmount());
        res.setRemainingAmount(d.getRemainingAmount());
        res.setDueDate(d.getDueDate());
        res.setStatus(d.getStatus());
        res.setNote(d.getNote());
        res.setCreatedAt(d.getCreatedAt());
        res.setUpdatedAt(d.getUpdatedAt());
        res.setDeleted(d.getDeletedAt() != null);
        return res;
    }

    @Transactional(readOnly = true)
    public DeltaResponse<DebtResponse> getDelta(String updatedSince, String untilTimestamp,
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
        Slice<Debt> slice = debtRepository.findDelta(
                user.getId(), since, until, cursorTs, lastId, PageRequest.of(0, limit));
        return new DeltaResponse<>(
                slice.getContent().stream().map(this::toResponse).toList(),
                slice.hasNext(),
                until.toString());
    }
}
