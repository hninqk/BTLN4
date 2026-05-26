package com.auction.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.auction.model.Bidder;
import com.auction.model.User;
import com.auction.repository.JdbcUserRepository;
import com.auction.security.PasswordHashService;
import com.auction.util.AppConfig;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

class UserServiceTest {

    private MockedStatic<AppConfig> mockedConfig;

    @BeforeEach
    void setUp() {
        mockedConfig = mockStatic(AppConfig.class);
        mockedConfig.when(AppConfig::jdbcUrl).thenReturn("jdbc:postgresql://localhost:5432/db");
    }

    @AfterEach
    void tearDown() {
        mockedConfig.close();
    }

    @Test
    void loginSuccess() {
        try (MockedConstruction<JdbcUserRepository> mocked = mockConstruction(JdbcUserRepository.class,
                (mock, context) -> {
                    User user = new Bidder("alice", PasswordHashService.hash("123"), 1000.0);
                    when(mock.findByUsername("alice")).thenReturn(Optional.of(user));
                })) {
            
            // We need to get a new instance of UserService to use the mocked repository
            // Since it's a singleton, we might need to use reflection to reset it or just 
            // hope it hasn't been initialized yet.
            // Better: use reflection to create a new instance.
            UserService userService = createNewInstance();
            
            Optional<User> result = userService.login("alice", "123");
            
            assertTrue(result.isPresent());
            assertEquals("alice", result.get().getUsername());
        }
    }

    @Test
    void loginFailWrongPassword() {
        try (MockedConstruction<JdbcUserRepository> mocked = mockConstruction(JdbcUserRepository.class,
                (mock, context) -> {
                    User user = new Bidder("alice", PasswordHashService.hash("123"), 1000.0);
                    when(mock.findByUsername("alice")).thenReturn(Optional.of(user));
                })) {
            
            UserService userService = createNewInstance();
            Optional<User> result = userService.login("alice", "wrong");
            
            assertFalse(result.isPresent());
        }
    }

    private UserService createNewInstance() {
        try {
            java.lang.reflect.Constructor<UserService> constructor = UserService.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            return constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
