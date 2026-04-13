package vn.edu.usth.tip.backend.dto.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import vn.edu.usth.tip.backend.models.enums.CategoryType;

import java.util.UUID;

@Data
public class CreateCategoryRequest {
    private UUID userId;        // null if system category
    private UUID parentId;      // null if no parent

    @NotBlank
    private String name;

    @NotNull
    private CategoryType type;

    private String icon;
    private String colorHex;
    private Boolean isSystem = false;
}
