package co.edu.unbosque.foodik.domain.dto.response;
import co.edu.unbosque.foodik.domain.enums.Role;
import java.time.LocalDateTime;
import java.util.UUID;
public record UserResponse(UUID id, String name, String email, String phone, Role role, LocalDateTime createdAt) {}
