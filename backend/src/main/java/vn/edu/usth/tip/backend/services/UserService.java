package vn.edu.usth.tip.backend.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.edu.usth.tip.backend.dto.user.CreateUserRequest;
import vn.edu.usth.tip.backend.dto.user.UserResponse;
import vn.edu.usth.tip.backend.exception.ResourceNotFoundException;
import vn.edu.usth.tip.backend.models.User;
import vn.edu.usth.tip.backend.repositories.UserRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public UserResponse createUser(CreateUserRequest req) {
        User user = new User();
        user.setEmail(req.getEmail());
        user.setPasswordHash(req.getPassword()); // TODO: hash password before saving
        user.setFullName(req.getFullName());
        user.setAvatarUrl(req.getAvatarUrl());
        user.setCurrencyCode(req.getCurrencyCode());
        user.setTimezone(req.getTimezone());
        return toResponse(userRepository.save(user));
    }

    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        return toResponse(user);
    }

    public UserResponse updateUser(UUID id, CreateUserRequest req) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        user.setFullName(req.getFullName());
        user.setAvatarUrl(req.getAvatarUrl());
        user.setCurrencyCode(req.getCurrencyCode());
        user.setTimezone(req.getTimezone());
        return toResponse(userRepository.save(user));
    }

    public void deleteUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
        userRepository.delete(user);
    }

    private UserResponse toResponse(User user) {
        UserResponse res = new UserResponse();
        res.setId(user.getId());
        res.setEmail(user.getEmail());
        res.setFullName(user.getFullName());
        res.setAvatarUrl(user.getAvatarUrl());
        res.setCurrencyCode(user.getCurrencyCode());
        res.setTimezone(user.getTimezone());
        res.setIsActive(user.getIsActive());
        res.setCreatedAt(user.getCreatedAt());
        return res;
    }
}
