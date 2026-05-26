package co.edu.unbosque.foodik.domain.dto.request;
import jakarta.validation.constraints.*;
public record UpdateUserRequest(
        @Size(max = 100) String name,
        @Size(max = 20) String phone
) {}
