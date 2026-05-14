package co.edu.unbosque.foodik.domain.dto.request;
import jakarta.validation.constraints.NotBlank;
public record RefreshTokenRequest(@NotBlank String refreshToken) {}
