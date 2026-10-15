package com.onatarslan.deeptodo.user;

import com.onatarslan.deeptodo.user.domain.User;
import com.onatarslan.deeptodo.user.domain.UserRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getMe(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();

        return user;
    }
}
