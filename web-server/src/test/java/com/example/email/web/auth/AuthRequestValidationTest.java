package com.example.email.web.auth;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll static void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll static void teardown() { factory.close(); }

    @Test
    void signup_rejectsBlankEmail() {
        var req = new AuthController.SignupRequest("", "password123", "X");
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    void signup_rejectsShortPassword() {
        var req = new AuthController.SignupRequest("a@b.c", "short", "X");
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    void signup_acceptsValid() {
        var req = new AuthController.SignupRequest("alice@example.com", "longerThan6", "Alice");
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void login_acceptsValid() {
        var req = new AuthController.LoginRequest("alice@example.com", "anything");
        assertThat(validator.validate(req)).isEmpty();
    }
}
