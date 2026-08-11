package by.egrius.payment_service.service;

import by.egrius.payment_service.dto.account.AccountCreateDto;
import by.egrius.payment_service.dto.account.AccountReadDto;
import by.egrius.payment_service.entity.Account;
import by.egrius.payment_service.exception.ResourceNotFoundException;
import by.egrius.payment_service.mapper.AccountMapper;
import by.egrius.payment_service.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
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

    @Autowired
    private CacheManager cacheManager;

    @Transactional
    @CachePut(cacheNames = {"accounts"}, key = "#publicUserId + '_' + #result.publicId()")
    public AccountReadDto createAccount(UUID publicUserId, AccountCreateDto dto) {

        Account account = Account.builder()
                .userId(publicUserId)
                .name(dto.name())
                .currency(dto.currency())
                .balance(BigDecimal.ZERO)
                .build();

        account = accountRepository.save(account);
        return accountMapper.toReadDto(account);
    }

    //TODO throw 404 from here, not 403 to let some guy know that this account exists
    @Cacheable(cacheNames = {"accounts"}, key = "#publicUserId + '_' + #publicAccountId")
    public AccountReadDto getAccountByPublicId(UUID publicAccountId, UUID publicUserId) {

        Account account = accountRepository.findByPublicIdAndUserId(publicAccountId, publicUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Account with id '%s' not found", publicAccountId)
                ));

        return accountMapper.toReadDto(account);
    }

    public List<AccountReadDto> getAllAccountsByUserId(UUID publicUserId) {
        List<AccountReadDto> results = accountRepository.findAllAccountsByUserId(publicUserId)
                .stream()
                .map(accountMapper::toReadDto)
                .toList();

        if (!results.isEmpty()) {
            Cache cache = cacheManager.getCache("accounts");
            if(cache != null) {
                for(AccountReadDto acc : results) {
                    String key = publicUserId + "_" + acc.publicId();
                    cache.put(key, acc);
                }
            }
        }
        return results;
    }

    //TODO throw 404 from here, not 403 to let some guy know that this account exists
    @CacheEvict(cacheNames = "accounts", key = "#publicUserId + '_' + #publicAccountId")
    @Transactional
    public void deleteAccount(UUID publicAccountId, UUID publicUserId) {

        Account account = accountRepository.findByPublicIdAndUserId(publicAccountId, publicUserId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format("Account with id '%s' not found", publicAccountId)
                ));

        accountRepository.delete(account);
    }
}