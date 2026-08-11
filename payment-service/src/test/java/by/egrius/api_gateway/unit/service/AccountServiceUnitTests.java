package by.egrius.api_gateway.unit.service;

import by.egrius.api_gateway.dto.account.AccountCreateDto;
import by.egrius.api_gateway.dto.account.AccountReadDto;
import by.egrius.api_gateway.entity.Account;
import by.egrius.api_gateway.exception.ResourceNotFoundException;
import by.egrius.api_gateway.mapper.AccountMapper;
import by.egrius.api_gateway.repository.AccountRepository;
import by.egrius.api_gateway.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceUnitTests {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountMapper accountMapper;

    @InjectMocks
    private AccountService accountService;

    private UUID userPublicId;
    private UUID accountPublicId;
    private Account account;
    private AccountReadDto accountReadDto;
    private AccountCreateDto createDto;

    @BeforeEach
    void setUp() {
        userPublicId = UUID.randomUUID();
        accountPublicId = UUID.randomUUID();

        account = Account.builder()
                .publicId(accountPublicId)
                .userId(userPublicId)
                .name("Main Account")
                .currency("USD")
                .balance(BigDecimal.ZERO)
                .build();

        accountReadDto = new AccountReadDto(
                accountPublicId,
                "Main Account",
                "USD",
                BigDecimal.ZERO
        );

        createDto = new AccountCreateDto("Main Account", "USD");
    }

    @Test
    void createAccount_ShouldCreateAndReturnAccount() {
        
        when(accountRepository.save(any(Account.class))).thenReturn(account);
        when(accountMapper.toReadDto(any(Account.class))).thenReturn(accountReadDto);

        
        AccountReadDto result = accountService.createAccount(userPublicId, createDto);

        
        assertThat(result).isNotNull();
        assertThat(result.publicId()).isEqualTo(accountPublicId);
        assertThat(result.name()).isEqualTo("Main Account");
        verify(accountRepository, times(1)).save(any(Account.class));
        verify(accountMapper, times(1)).toReadDto(any(Account.class));
    }

    @Test
    void getAccountByPublicId_WhenAccountExists_ShouldReturnAccount() {
        
        when(accountRepository.findByPublicIdAndUserId(accountPublicId, userPublicId))
                .thenReturn(Optional.of(account));
        when(accountMapper.toReadDto(account)).thenReturn(accountReadDto);

        
        AccountReadDto result = accountService.getAccountByPublicId(accountPublicId, userPublicId);

        
        assertThat(result).isNotNull();
        assertThat(result.publicId()).isEqualTo(accountPublicId);
        verify(accountRepository, times(1))
                .findByPublicIdAndUserId(accountPublicId, userPublicId);
    }

    @Test
    void getAccountByPublicId_WhenAccountNotFound_ShouldThrowResourceNotFoundException() {
        
        when(accountRepository.findByPublicIdAndUserId(accountPublicId, userPublicId))
                .thenReturn(Optional.empty());

        
        assertThatThrownBy(() -> accountService.getAccountByPublicId(accountPublicId, userPublicId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(accountPublicId.toString());

        verify(accountRepository, times(1))
                .findByPublicIdAndUserId(accountPublicId, userPublicId);
        verify(accountMapper, never()).toReadDto(any());
    }

    @Test
    void getAllAccountsByUserId_ShouldReturnListOfAccounts() {
        
        List<Account> accounts = List.of(account);
        List<AccountReadDto> expectedDtos = List.of(accountReadDto);

        when(accountRepository.findAllAccountsByUserId(userPublicId)).thenReturn(accounts);
        when(accountMapper.toReadDto(account)).thenReturn(accountReadDto);

        
        List<AccountReadDto> result = accountService.getAllAccountsByUserId(userPublicId);

        
        assertThat(result).hasSize(1);
        assertThat(result.get(0).publicId()).isEqualTo(accountPublicId);
        verify(accountRepository, times(1)).findAllAccountsByUserId(userPublicId);
    }

    @Test
    void deleteAccount_WhenAccountExists_ShouldDeleteAccount() {
        
        when(accountRepository.findByPublicIdAndUserId(accountPublicId, userPublicId))
                .thenReturn(Optional.of(account));
        doNothing().when(accountRepository).delete(account);

        
        accountService.deleteAccount(accountPublicId, userPublicId);

        
        verify(accountRepository, times(1))
                .findByPublicIdAndUserId(accountPublicId, userPublicId);
        verify(accountRepository, times(1)).delete(account);
    }

    @Test
    void deleteAccount_WhenAccountNotFound_ShouldThrowResourceNotFoundException() {
        
        when(accountRepository.findByPublicIdAndUserId(accountPublicId, userPublicId))
                .thenReturn(Optional.empty());

        
        assertThatThrownBy(() -> accountService.deleteAccount(accountPublicId, userPublicId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(accountPublicId.toString());

        verify(accountRepository, times(1))
                .findByPublicIdAndUserId(accountPublicId, userPublicId);
        verify(accountRepository, never()).delete(any());
    }
}