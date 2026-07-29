package by.egrius.api_gateway.service;

import by.egrius.api_gateway.dto.account.AccountCreateDto;
import by.egrius.api_gateway.dto.account.AccountReadDto;
import by.egrius.api_gateway.entity.Account;
import by.egrius.api_gateway.exception.ResourceNotFoundException;
import by.egrius.api_gateway.mapper.AccountMapper;
import by.egrius.api_gateway.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    /*
    @Transactional
    public AccountReadDto createAccount(UUID userPublicId, AccountCreateDto dto) {
        User user = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userPublicId));

        Account account = Account.builder()
                .user(user)
                .name(dto.name())
                .currency(dto.currency())
                .balance(BigDecimal.ZERO)
                .build();

        account = accountRepository.save(account);
        return accountMapper.toReadDto(account);
    }

    
    public AccountReadDto getAccountByPublicId(UUID publicId) {
        Account account = accountRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + publicId));
        return accountMapper.toReadDto(account);
    }

    
    public List<AccountReadDto> getAccountsByUser(UUID userPublicId) {
        User user = userRepository.findByPublicId(userPublicId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userPublicId));

        return accountRepository.findByUser(user)
                .stream()
                .map(accountMapper::toReadDto)
                .toList();
    }

    @Transactional
    public void deleteAccount(UUID publicId) {
        Account account = accountRepository.findByPublicId(publicId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + publicId));
        accountRepository.delete(account);
    }

     */

}