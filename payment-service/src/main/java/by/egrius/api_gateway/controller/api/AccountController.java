package by.egrius.api_gateway.controller.api;

import by.egrius.api_gateway.annotation.CurrentUser;
import by.egrius.api_gateway.dto.account.AccountCreateDto;
import by.egrius.api_gateway.dto.account.AccountReadDto;
import by.egrius.api_gateway.dto.user.CurrentUserDto;
import by.egrius.api_gateway.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @GetMapping
    public List<AccountReadDto> getAllUserAccounts(@CurrentUser CurrentUserDto currentUserDto) {
        return accountService.getAllAccountsByUserId(currentUserDto.publicId());
    }

    @PostMapping("/create")
        public ResponseEntity<AccountReadDto> createAccount(
            @CurrentUser CurrentUserDto currentUserDto,
            @Valid @RequestBody AccountCreateDto dto
    ) {
        AccountReadDto account = accountService.createAccount(currentUserDto.publicId(), dto);
        return ResponseEntity
                .created(URI.create("/api/accounts/" + account.publicId()))
                .body(account);
    }

    // Add caching
    @GetMapping("/{public-account-id}")
    public AccountReadDto getAccount(@PathVariable("public-account-id") UUID publicAccountId,
                                     @CurrentUser CurrentUserDto currentUserDto) {
        return accountService.getAccountByPublicId(publicAccountId, currentUserDto.publicId());
    }

    @DeleteMapping("/{public-account-id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable("public-account-id") UUID publicAccountId,
                                              @CurrentUser CurrentUserDto currentUserDto) {
        accountService.deleteAccount(publicAccountId, currentUserDto.publicId());
        return ResponseEntity.noContent().build();
    }
}