package by.egrius.payment_service.controller.api;

import by.egrius.payment_service.annotation.CurrentUser;
import by.egrius.payment_service.dto.account.AccountCreateDto;
import by.egrius.payment_service.dto.account.AccountReadDto;
import by.egrius.payment_service.dto.response.ErrorResponse;
import by.egrius.payment_service.dto.user.CurrentUserDto;
import by.egrius.payment_service.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Tag(name = "Accounts", description = "Accounts management")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @Operation(summary = "Get all user accounts",
            description = "Returns all accounts owned by the current authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "List of accounts"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public List<AccountReadDto> getAllUserAccounts(@CurrentUser CurrentUserDto currentUserDto) {
        return accountService.getAllAccountsByUserId(currentUserDto.publicId());
    }

    @Operation(summary = "Create account",
              description = "Creates a new account for the current authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "400", description = "Validation exception", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<AccountReadDto> createAccount(
            @CurrentUser CurrentUserDto currentUserDto,
            @Valid @RequestBody AccountCreateDto dto
    ) {
        AccountReadDto account = accountService.createAccount(currentUserDto.publicId(), dto);

        return ResponseEntity
                .created(URI.create("/api/accounts/" + account.publicId()))
                .body(account);
    }

    @Operation(summary = "Get account by public ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Account found"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found or not owned by user", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{public-account-id}")
    public AccountReadDto getAccount(
            @Parameter(description = "Account public ID",
                    example = "22222222-2222-2222-2222-222222222222")
            @PathVariable("public-account-id") UUID publicAccountId,
                                     @CurrentUser CurrentUserDto currentUserDto) {
        return accountService.getAccountByPublicId(publicAccountId, currentUserDto.publicId());
    }

    @Operation(summary = "Delete account")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Account deleted"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Account not found or not owned by user", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{public-account-id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable("public-account-id") UUID publicAccountId,
                                              @CurrentUser CurrentUserDto currentUserDto) {
        accountService.deleteAccount(publicAccountId, currentUserDto.publicId());
        return ResponseEntity.noContent().build();
    }
}