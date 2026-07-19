package by.egrius.api_gateway.controller;

import by.egrius.api_gateway.dto.account.AccountCreateDto;
import by.egrius.api_gateway.dto.account.AccountReadDto;
import by.egrius.api_gateway.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/user/{userPublicId}")
    public ResponseEntity<AccountReadDto> createAccount(
            @PathVariable UUID userPublicId,
            @Valid @RequestBody AccountCreateDto dto
    ) {
        AccountReadDto account = accountService.createAccount(userPublicId, dto);
        return ResponseEntity
                .created(URI.create("/api/accounts/" + account.publicId()))
                .body(account);
    }

    @GetMapping("/{publicId}")
    public AccountReadDto getAccount(@PathVariable UUID publicId) {
        return accountService.getAccountByPublicId(publicId);
    }

    @GetMapping("/user/{userPublicId}")
    public List<AccountReadDto> getAccountsByUser(@PathVariable UUID userPublicId) {
        return accountService.getAccountsByUser(userPublicId);
    }

    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> deleteAccount(@PathVariable UUID publicId) {
        accountService.deleteAccount(publicId);
        return ResponseEntity.noContent().build();
    }
}