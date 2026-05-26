package co.edu.unbosque.foodik.domain.dto.response;
import co.edu.unbosque.foodik.domain.enums.DiscountType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
public record DiscountResponse(UUID id, UUID restaurantId, String title, String description,
                                DiscountType discountType, BigDecimal discountValue,
                                LocalDateTime validFrom, LocalDateTime validTo, Boolean isActive) {}
