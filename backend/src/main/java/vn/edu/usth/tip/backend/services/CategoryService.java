package vn.edu.usth.tip.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.usth.tip.backend.dto.category.CategoryResponse;
import vn.edu.usth.tip.backend.dto.category.CreateCategoryRequest;
import vn.edu.usth.tip.backend.dto.common.DeltaResponse;
import vn.edu.usth.tip.backend.exception.ResourceNotFoundException;
import vn.edu.usth.tip.backend.models.Category;
import vn.edu.usth.tip.backend.models.User;
import vn.edu.usth.tip.backend.repositories.CategoryRepository;
import vn.edu.usth.tip.backend.repositories.TransactionRepository;
import vn.edu.usth.tip.backend.repositories.UserRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public CategoryResponse createCategory(CreateCategoryRequest req) {
        Category category = new Category();
        if (req.getUserId() != null) {
            User user = userRepository.findById(req.getUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", "id", req.getUserId()));
            category.setUser(user);
        }
        if (req.getParentId() != null) {
            Category parent = categoryRepository.findById(req.getParentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Category", "id", req.getParentId()));
            category.setParent(parent);
        }
        category.setName(req.getName());
        category.setType(req.getType());
        category.setIcon(req.getIcon());
        category.setColorHex(req.getColorHex());
        category.setIsSystem(req.getIsSystem() != null ? req.getIsSystem() : false);
        return toResponse(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoriesByUser(UUID userId) {
        return categoryRepository.findByUserId(userId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", email));
        return categoryRepository.findSystemAndUserCategories(user.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public void deleteCategory(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
        if (Boolean.TRUE.equals(category.getIsSystem())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete system category");
        }
        // Reassign orphan transactions to system "Khác" before soft-deleting (avoids dangling FK)
        Category fallback = categoryRepository.findByNameAndUserIsNull("Khác")
                .orElseThrow(() -> new ResourceNotFoundException("Category", "name", "Khác"));
        transactionRepository.reassignCategoryId(id, fallback.getId());
        OffsetDateTime now = OffsetDateTime.now();
        category.setDeletedAt(now);
        category.setUpdatedAt(now);
        categoryRepository.save(category);
    }

    private CategoryResponse toResponse(Category c) {
        CategoryResponse res = new CategoryResponse();
        res.setId(c.getId());
        res.setUserId(c.getUser() != null ? c.getUser().getId() : null);
        res.setParentId(c.getParent() != null ? c.getParent().getId() : null);
        res.setName(c.getName());
        res.setType(c.getType());
        res.setIcon(c.getIcon());
        res.setColorHex(c.getColorHex());
        res.setIsSystem(c.getIsSystem());
        res.setCreatedAt(c.getCreatedAt());
        res.setUpdatedAt(c.getUpdatedAt());
        res.setDeleted(c.getDeletedAt() != null);
        return res;
    }

    @Transactional(readOnly = true)
    public DeltaResponse<CategoryResponse> getDelta(String updatedSince, String untilTimestamp,
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
        Slice<Category> slice = categoryRepository.findDelta(
                user.getId(), since, until, cursorTs, lastId, PageRequest.of(0, limit));
        return new DeltaResponse<>(
                slice.getContent().stream().map(this::toResponse).toList(),
                slice.hasNext(),
                until.toString());
    }
}
