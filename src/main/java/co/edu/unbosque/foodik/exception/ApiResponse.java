package co.edu.unbosque.foodik.exception;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(boolean success, String message, T data, String timestamp, List<String> errors) {
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, Instant.now().toString(), null);
    }
    public static <T> ApiResponse<T> ok(T data) { return ok("OK", data); }
    public static ApiResponse<Void> error(String message, List<String> errors) {
        return new ApiResponse<>(false, message, null, Instant.now().toString(), errors);
    }
    public static ApiResponse<Void> error(String message) { return error(message, null); }
}
