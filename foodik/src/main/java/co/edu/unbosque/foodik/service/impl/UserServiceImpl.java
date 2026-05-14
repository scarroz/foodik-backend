package co.edu.unbosque.foodik.service.impl;
import co.edu.unbosque.foodik.domain.dto.request.UpdateUserRequest;
import co.edu.unbosque.foodik.domain.dto.response.UserResponse;
import co.edu.unbosque.foodik.domain.entity.User;
import co.edu.unbosque.foodik.exception.ResourceNotFoundException;
import co.edu.unbosque.foodik.repository.UserRepository;
import co.edu.unbosque.foodik.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    public UserServiceImpl(UserRepository userRepository) { this.userRepository = userRepository; }
    @Override @Transactional(readOnly = true)
    public UserResponse getMe(String email) {
        return toResponse(userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found")));
    }
    @Override @Transactional
    public UserResponse update(String email, UpdateUserRequest request) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (request.name() != null) user.setName(request.name());
        if (request.phone() != null) user.setPhone(request.phone());
        return toResponse(userRepository.save(user));
    }
    @Override @Transactional
    public void delete(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        userRepository.delete(user);
    }
    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole(), u.getCreatedAt());
    }
}
