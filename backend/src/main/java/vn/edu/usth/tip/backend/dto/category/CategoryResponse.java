package vn.edu.usth.tip.backend.dto.category;

import lombok.Data;
import vn.edu.usth.tip.backend.models.enums.CategoryType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class CategoryResponse {
    private UUID id;
    private UUID userId;
    private UUID parentId;
    private String name;
    private CategoryType type;
    private String icon;
    private String colorHex;
    private Boolean isSystem;
    private OffsetDateTime createdAt;
}
