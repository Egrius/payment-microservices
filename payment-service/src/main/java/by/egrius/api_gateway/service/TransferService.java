    package by.egrius.api_gateway.service;

    import by.egrius.api_gateway.dto.transfer.AdminSenderLeaderboardDto;
    import by.egrius.api_gateway.dto.transfer.TransferCreateDto;
    import by.egrius.api_gateway.dto.transfer.TransferReadDto;
    import by.egrius.api_gateway.entity.Account;
    import by.egrius.api_gateway.entity.Transfer;
    import by.egrius.api_gateway.entity.TransferStatus;
    import by.egrius.api_gateway.event.TransferAddedEvent;
    import by.egrius.api_gateway.event.publisher.TransferAddedEventPublisher;
    import by.egrius.api_gateway.mapper.TransferMapper;
    import by.egrius.api_gateway.repository.AccountRepository;
    import by.egrius.api_gateway.repository.TransferRepository;
    import by.egrius.api_gateway.repository.projection.TransferProjection;
    import lombok.RequiredArgsConstructor;
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

            if(createDto.fromAccountId().equals(createDto.toAccountId())) {
                throw new IllegalArgumentException("Can't create transfer for the same account");
            }

            // Find accounts for the transfer
            Account fromAccount = accountRepository.findByPublicIdAndUserId(createDto.fromAccountId(), publicUserId)
                    .orElseThrow(
                        () -> new RuntimeException("'from account' does not exist"));

            Account toAccount = accountRepository.findByPublicId(createDto.toAccountId())
                    .orElseThrow(
                        () -> new RuntimeException("'to account' does not exist"));

            // Creating PENDING transfer not to hold the http thread and process the transfer independently
            Transfer transfer = Transfer.builder()
                    .fromAccount(fromAccount)
                    .toAccount(toAccount)
                    .amount(createDto.amount())
                    .status(TransferStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .build();

            Transfer savedTransfer = transferRepository.save(transfer);

            TransferReadDto savedTransferReadDto = transferMapper.mapToReadDto(savedTransfer);

            Cache cache = cacheManager.getCache("transfers");
            if(cache != null) {
                String key = publicUserId + "_" + savedTransferReadDto.publicId();
                cache.put(key, savedTransferReadDto);
            }

            // Publish an event to process the transfer in TransferAddedEventListener
            transferAddedEventPublisher.publishEvent(
                    new TransferAddedEvent(this, fromAccount.getId(), toAccount.getId(), savedTransfer.getId())
            );

            return savedTransferReadDto;
        }

        @Cacheable(cacheNames = "transfers", key = "#userId + '_' + #publicTransferId")
        public TransferReadDto getTransferStatus(UUID publicTransferId, UUID userId) {

            Transfer transfer = transferRepository.findByTransferPublicId_and_UserPublicId(publicTransferId, userId).orElseThrow(
                    () -> new RuntimeException("Couldn't find a transfer with id %s".formatted(publicTransferId)));

            return transferMapper.mapToReadDto(transfer);
        }

        public List<TransferReadDto> get_10_LatestTransfersByUser(UUID userId) {
            PageRequest pageable = PageRequest.of(0, 10);

            Page<TransferProjection> page = transferRepository.get_10_LatestTransfersByUserId(userId, pageable);

            return page.getContent().stream().map(TransferReadDto::fromProjection).toList();
        }

        public List<AdminSenderLeaderboardDto> getSenderLeaderboard(Integer leaderboardLimit, Integer daysCount) {
            if (leaderboardLimit == null || daysCount == null) {
                throw new RuntimeException(
                        "Can't create leaderboard with null params: leaderboardLimit {" + leaderboardLimit
                                + "} , daysCount {" + daysCount + "} ");
            }
            return transferRepository.findTopUsersByTransferCount(leaderboardLimit, daysCount).stream()
                    .map(AdminSenderLeaderboardDto::fromProjection)
                    .toList();
        }
    }