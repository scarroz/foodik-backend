package co.edu.unbosque.foodik.service.impl;
import co.edu.unbosque.foodik.domain.dto.request.UpdateUserRequest;
import co.edu.unbosque.foodik.domain.dto.response.UserResponse;
import co.edu.unbosque.foodik.domain.entity.User;
import co.edu.unbosque.foodik.exception.ResourceNotFoundException;
import co.edu.unbosque.foodik.repository.UserRepository;
import co.edu.unbosque.foodik.service.UserService;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

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

    @Override
    public UserResponse findByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getPhone(), user.getRole(), user.getCreatedAt());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getPhone(), user.getRole(), user.getCreatedAt());
    }

}
