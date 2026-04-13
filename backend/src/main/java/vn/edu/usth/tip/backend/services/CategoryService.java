package vn.edu.usth.tip.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.edu.usth.tip.backend.dto.category.CategoryResponse;
import vn.edu.usth.tip.backend.dto.category.CreateCategoryRequest;
import vn.edu.usth.tip.backend.exception.ResourceNotFoundException;
import vn.edu.usth.tip.backend.models.Category;
import vn.edu.usth.tip.backend.models.User;
import vn.edu.usth.tip.backend.repositories.CategoryRepository;
import vn.edu.usth.tip.backend.repositories.UserRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
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

    public List<CategoryResponse> getCategoriesByUser(UUID userId) {
        return categoryRepository.findByUserId(userId).stream()
                .map(this::toResponse).collect(Collectors.toList());
    }

    public void deleteCategory(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
        categoryRepository.delete(category);
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
        return res;
    }
}
