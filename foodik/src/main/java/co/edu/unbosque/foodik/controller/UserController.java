package co.edu.unbosque.foodik.controller;

import co.edu.unbosque.foodik.domain.dto.request.UpdateUserRequest;
import co.edu.unbosque.foodik.domain.dto.response.UserResponse;
import co.edu.unbosque.foodik.exception.ApiResponse;
import co.edu.unbosque.foodik.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
public class UserController {

    private final UserService userService;
    public UserController(UserService userService) { this.userService = userService; }

    @GetMapping("/me")
    @Operation(summary = "Get authenticated user profile")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getMe(userDetails.getUsername())));
    }

    @PutMapping("/me")
    @Operation(summary = "Update authenticated user profile")
    public ResponseEntity<ApiResponse<UserResponse>> update(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Profile updated", userService.update(userDetails.getUsername(), request)));
    }

    @DeleteMapping("/me")
    @Operation(summary = "Delete authenticated user account")
    public ResponseEntity<ApiResponse<Void>> delete(@AuthenticationPrincipal UserDetails userDetails) {
        userService.delete(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Account deleted", null));
    }
}
