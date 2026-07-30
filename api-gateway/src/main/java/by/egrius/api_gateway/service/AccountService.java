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

    @Transactional
    public AccountReadDto createAccount(UUID userPublicId, AccountCreateDto dto) {

        Account account = Account.builder()
                .userId(userPublicId)
                .name(dto.name())
                .currency(dto.currency())
                .balance(BigDecimal.ZERO)
                .build();

        account = accountRepository.save(account);
        return accountMapper.toReadDto(account);
    }

    //TODO throw 404 from here, not 403 to let some guy know that this account exists
    public AccountReadDto getAccountByPublicId(UUID publicAccountId, UUID publicUserId) {

        Account account = accountRepository.findByPublicIdAndUserId(publicAccountId, publicUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Account with id '%s' not found", publicAccountId)
                ));

        return accountMapper.toReadDto(account);
    }

    
    public List<AccountReadDto> getAllAccountsByUserId(UUID publicUserId) {
        return accountRepository.findAllAccountsByUserId(publicUserId)
                .stream()
                .map(accountMapper::toReadDto)
                .toList();
    }

    //TODO throw 404 from here, not 403 to let some guy know that this account exists
    @Transactional
    public void deleteAccount(UUID publicAccountId, UUID publicUserId) {

        Account account = accountRepository.findByPublicIdAndUserId(publicAccountId, publicUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Account with id '%s' not found", publicAccountId)
                ));

        accountRepository.delete(account);
    }

}