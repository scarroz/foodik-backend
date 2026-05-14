package co.edu.unbosque.foodik.service;
import co.edu.unbosque.foodik.domain.dto.request.UpdateUserRequest;
import co.edu.unbosque.foodik.domain.dto.response.UserResponse;
public interface UserService {
    UserResponse getMe(String email);
    UserResponse update(String email, UpdateUserRequest request);
    void delete(String email);
}
