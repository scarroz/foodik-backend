package co.edu.unbosque.foodik.domain.dto.request;
import jakarta.validation.constraints.*;
public record RegisterRequest(
        @NotBlank(message = "Name is required") @Size(max = 100) String name,
        @NotBlank @Email(message = "Invalid email") String email,
        @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
        @Size(max = 20) String phone
) {}
