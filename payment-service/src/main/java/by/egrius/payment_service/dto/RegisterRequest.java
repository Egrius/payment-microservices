package by.egrius.payment_service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotEmpty
        String username,

        @NotEmpty
        @Size(min = 5)
        String password,

        @NotEmpty
        @Email
        String email
) {}
