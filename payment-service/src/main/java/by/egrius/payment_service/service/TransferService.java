package by.egrius.payment_service.service;

import by.egrius.payment_service.dto.transfer.AdminSenderLeaderboardDto;
import by.egrius.payment_service.dto.transfer.TransferCreateDto;
import by.egrius.payment_service.dto.transfer.TransferReadDto;
import by.egrius.payment_service.entity.Account;
import by.egrius.payment_service.entity.Transfer;
import by.egrius.payment_service.entity.TransferStatus;
import by.egrius.payment_service.event.TransferAddedEvent;
import by.egrius.payment_service.event.publisher.TransferAddedEventPublisher;
import by.egrius.payment_service.exception.payment_service.AccountNotFoundException;
import by.egrius.payment_service.exception.payment_service.InvalidLeaderboardParamsException;
import by.egrius.payment_service.exception.payment_service.SameAccountTransferException;
import by.egrius.payment_service.exception.payment_service.TransferNotFoundException;
import by.egrius.payment_service.mapper.TransferMapper;
import by.egrius.payment_service.repository.AccountRepository;
import by.egrius.payment_service.repository.TransferRepository;
import by.egrius.payment_service.repository.projection.TransferProjection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final TransferMapper transferMapper;

    private final TransferAddedEventPublisher transferAddedEventPublisher;

    @Autowired
    private CacheManager cacheManager;

    /**
     * Creates a transfer
     * - Saves a transfer in the DB with PENDING status
     * - Creates an event for the created transfer to process the transfer independently
     * - Returns dto contains transfer's info (pending one)
     */
    @Transactional
    public TransferReadDto createTransfer(TransferCreateDto createDto, UUID publicUserId) {
        log.debug("Creating transfer: from={}, to={}, amount={}",
                createDto.fromAccountPublicId(), createDto.toAccountPublicId(), createDto.amount());

        UUID fromAccountPublicId = createDto.fromAccountPublicId();
        UUID toAccountPublicId = createDto.toAccountPublicId();

        if(fromAccountPublicId.equals(toAccountPublicId)) {
            throw new SameAccountTransferException(fromAccountPublicId);
        }

        // Find accounts for the transfer
        Account fromAccount = accountRepository.findByPublicIdAndUserId(fromAccountPublicId, publicUserId)
                .orElseThrow(
                    () -> new AccountNotFoundException("'from account' does not exist"));

        Account toAccount = accountRepository.findByPublicId(toAccountPublicId)
                .orElseThrow(
                    () -> new AccountNotFoundException("'to account' does not exist"));

        // Creating PENDING transfer not to hold the http thread and process the transfer independently
        Transfer transfer = Transfer.builder()
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(createDto.amount())
                .status(TransferStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        Transfer savedTransfer = transferRepository.save(transfer);
        log.info("Created pending transfer: id={}, status={}", savedTransfer.getPublicId(), TransferStatus.PENDING);

        TransferReadDto savedTransferReadDto = transferMapper.mapToReadDto(savedTransfer);

        Cache cache = cacheManager.getCache("transfers");
        if(cache != null) {
            String key = publicUserId + "_" + savedTransferReadDto.publicId();
            cache.put(key, savedTransferReadDto);
        }

        // Publish an event to process the transfer in TransferAddedEventListener
        transferAddedEventPublisher.publishEvent(
                new TransferAddedEvent(
                        this,
                        fromAccount.getId(),
                        toAccount.getId(),
                        savedTransfer.getId()
                )
        );

        return savedTransferReadDto;
    }

    @Cacheable(cacheNames = "transfers", key = "#userId + '_' + #publicTransferId")
    public TransferReadDto getTransferStatus(UUID publicTransferId, UUID userId) {

        log.debug("Getting transfer status: id={}, userId={}", publicTransferId, userId);

        Transfer transfer = transferRepository
                .findByTransferPublicId_and_UserPublicId(publicTransferId, userId)
                .orElseThrow(() -> new TransferNotFoundException(publicTransferId));

        return transferMapper.mapToReadDto(transfer);
    }

    // TODO null check
    public List<TransferReadDto> get_10_LatestTransfersByPublicUserId(UUID userId) {

        PageRequest pageable = PageRequest.of(0, 10);

        Page<TransferProjection> page = transferRepository.get_10_LatestTransfersByUserId(userId, pageable);

        return page.getContent().stream().map(TransferReadDto::fromProjection).toList();
    }

    public List<AdminSenderLeaderboardDto> getSenderLeaderboard(Integer leaderboardLimit, Integer daysCount) {

        log.debug("Getting sender leaderboard: limit={}, days={}", leaderboardLimit, daysCount);

        if (leaderboardLimit == null || daysCount == null) {
            throw new InvalidLeaderboardParamsException(leaderboardLimit, daysCount);
        }

        if (leaderboardLimit <= 0) {
            throw new InvalidLeaderboardParamsException(
                    String.format("Leaderboard limit must be positive, got: %d", leaderboardLimit),
                    leaderboardLimit,
                    daysCount
            );
        }

        if (daysCount <= 0) {
            throw new InvalidLeaderboardParamsException(
                    String.format("Days count must be positive, got: %d", daysCount),
                    leaderboardLimit,
                    daysCount
            );
        }

        if (leaderboardLimit > 1000) {
            throw new InvalidLeaderboardParamsException(
                    String.format("Leaderboard limit cannot exceed 1000, got: %d", leaderboardLimit),
                    leaderboardLimit,
                    daysCount
            );
        }

        return transferRepository.findTopUsersByTransferCount(leaderboardLimit, daysCount).stream()
                .map(AdminSenderLeaderboardDto::fromProjection)
                .toList();
    }
}