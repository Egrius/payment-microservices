package by.Egrius.auth_server.service;

import by.Egrius.auth_server.dto.user.UserCreateDto;
import by.Egrius.auth_server.dto.user.UserReadDto;
import by.Egrius.auth_server.dto.user.UserUpdateDto;
import by.Egrius.auth_server.entity.User;
import by.Egrius.auth_server.exception.UserNotFoundException;
import by.Egrius.auth_server.mapper.UserMapper;
import by.Egrius.auth_server.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional
    public UserReadDto createUser(UserCreateDto dto) {
        if (userRepository.existsByEmail(dto.email())) {
            throw new RuntimeException("User with email %s already exists".formatted(dto.email()));
        }

        User user = User.builder()
                .username(dto.username())
                .email(dto.email())
                .build();

        user = userRepository.save(user);
        return userMapper.toReadDto(user);
    }

    public UserReadDto getUserByPublicId(UUID publicId) {
        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + publicId));
        return userMapper.toReadDto(user);
    }

    public List<UserReadDto> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(userMapper::toReadDto)
                .toList();
    }

    @Transactional
    public UserReadDto updateUser(UUID publicId, UserUpdateDto dto) {
        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + publicId));

        user.setUsername(dto.username());
        user.setEmail(dto.email());

        user = userRepository.save(user);
        return userMapper.toReadDto(user);
    }

    @Transactional
    public void deleteUser(UUID publicId) {
        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + publicId));
        userRepository.delete(user);
    }
}