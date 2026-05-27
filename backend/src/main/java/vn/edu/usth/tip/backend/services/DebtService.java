package vn.edu.usth.tip.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.usth.tip.backend.dto.common.DeltaResponse;
import vn.edu.usth.tip.backend.dto.debt.CreateDebtRequest;
import vn.edu.usth.tip.backend.dto.debt.DebtResponse;
import vn.edu.usth.tip.backend.exception.ResourceNotFoundException;
import vn.edu.usth.tip.backend.models.Debt;
import vn.edu.usth.tip.backend.models.User;
import vn.edu.usth.tip.backend.models.enums.DebtStatus;
import vn.edu.usth.tip.backend.repositories.DebtRepository;
import vn.edu.usth.tip.backend.utils.SecurityUtils;
import vn.edu.usth.tip.backend.utils.SyncConstants;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DebtService {

    private final DebtRepository debtRepository;
    private final SecurityUtils securityUtils;

    @Transactional
    public DebtResponse createDebt(CreateDebtRequest req) {
        User user = securityUtils.getCurrentUser();
        Debt debt = new Debt();
        debt.setUser(user);
        debt.setContactName(req.getContactName());
        debt.setContactPhone(req.getContactPhone());
        debt.setType(req.getType());
        debt.setAmount(req.getAmount());
        debt.setRemainingAmount(req.getAmount());
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
        return getDebtsByUser(securityUtils.getCurrentUser().getId());
    }

    @Transactional
    public DebtResponse settleDebt(UUID id) {
        Debt debt = debtRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Debt", "id", id));
        if (!debt.getUser().getId().equals(securityUtils.getCurrentUser().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        debt.setRemainingAmount(BigDecimal.ZERO);
        debt.setStatus(DebtStatus.settled);
        return toResponse(debtRepository.save(debt));
    }

    @Transactional
    public void deleteDebt(UUID id) {
        Debt debt = debtRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Debt", "id", id));
        if (!debt.getUser().getId().equals(securityUtils.getCurrentUser().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        OffsetDateTime now = OffsetDateTime.now();
        debt.setDeletedAt(now);
        debt.setUpdatedAt(now);
        debtRepository.save(debt);
    }

    private DebtResponse toResponse(Debt debt) {
        DebtResponse res = new DebtResponse();
        res.setId(debt.getId());
        res.setUserId(debt.getUser().getId());
        res.setContactName(debt.getContactName());
        res.setContactPhone(debt.getContactPhone());
        res.setType(debt.getType());
        res.setAmount(debt.getAmount());
        res.setRemainingAmount(debt.getRemainingAmount());
        res.setDueDate(debt.getDueDate());
        res.setStatus(debt.getStatus());
        res.setNote(debt.getNote());
        res.setCreatedAt(debt.getCreatedAt());
        res.setUpdatedAt(debt.getUpdatedAt());
        res.setDeleted(debt.getDeletedAt() != null);
        return res;
    }

    @Transactional(readOnly = true)
    public DeltaResponse<DebtResponse> getDelta(String updatedSince, String untilTimestamp,
                                                 String lastUpdatedAt, UUID lastId, int limit) {
        User user = securityUtils.getCurrentUser();
        OffsetDateTime since = updatedSince != null
                ? OffsetDateTime.parse(updatedSince)
                : OffsetDateTime.parse(SyncConstants.EPOCH_CURSOR);
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
