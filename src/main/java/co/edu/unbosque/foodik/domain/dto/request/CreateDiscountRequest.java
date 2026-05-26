package co.edu.unbosque.foodik.domain.dto.request;
import co.edu.unbosque.foodik.domain.enums.DiscountType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record CreateDiscountRequest(
        @NotBlank @Size(max = 150) String title,
        String description,
        @NotNull DiscountType discountType,
        @NotNull @DecimalMin("0.01") BigDecimal discountValue,
        @NotNull LocalDateTime validFrom,
        @NotNull LocalDateTime validTo
) {}
