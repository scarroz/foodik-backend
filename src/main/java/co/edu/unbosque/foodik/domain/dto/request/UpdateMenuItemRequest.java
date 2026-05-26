package co.edu.unbosque.foodik.domain.dto.request;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record UpdateMenuItemRequest(
        @Size(max = 150) String name,
        String description,
        @DecimalMin("0.01") BigDecimal price,
        String imageUrl,
        @Size(max = 80) String category,
        Boolean isAvailable
) {}
