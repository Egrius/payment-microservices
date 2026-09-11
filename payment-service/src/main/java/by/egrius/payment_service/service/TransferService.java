    package by.egrius.payment_service.service;

    import by.egrius.payment_service.dto.transfer.AdminSenderLeaderboardDto;
    import by.egrius.payment_service.dto.transfer.TransferCreateDto;
    import by.egrius.payment_service.dto.transfer.TransferReadDto;
    import by.egrius.payment_service.entity.Account;
    import by.egrius.payment_service.entity.Transfer;
    import by.egrius.payment_service.entity.TransferStatus;
    import by.egrius.payment_service.event.TransferAddedEvent;
    import by.egrius.payment_service.exception.payment_service.AccountNotFoundException;
    import by.egrius.payment_service.exception.payment_service.SameAccountTransferException;
    import by.egrius.payment_service.exception.payment_service.TransferNotFoundException;
    import by.egrius.payment_service.mapper.TransferMapper;
    import by.egrius.payment_service.repository.AccountRepository;
    import by.egrius.payment_service.repository.TransferRepository;
    import by.egrius.payment_service.repository.projection.TransferProjection;
    import lombok.RequiredArgsConstructor;
    import lombok.extern.slf4j.Slf4j;
    import org.springframework.amqp.rabbit.core.RabbitTemplate;
    import org.springframework.cache.annotation.Cacheable;
    import org.springframework.data.domain.Page;
    import org.springframework.data.domain.PageRequest;
    import org.springframework.stereotype.Service;
    import org.springframework.transaction.annotation.Transactional;
    import org.springframework.transaction.support.TransactionSynchronization;
    import org.springframework.transaction.support.TransactionSynchronizationManager;

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

        private final CacheService cacheService;

        private final RabbitTemplate rabbitTemplate;

        private static final String EXCHANGE_NAME = "processing.exchange";
        private static final String ROUTING_KEY = "transfer.processing";

        /**
         * Creates a transfer
         * - Saves a transfer in the DB with PENDING status
         * - Sends event to RabbitMQ after transaction commit
         * - Returns DTO with transfer info
         */
        @Transactional
        public TransferReadDto createTransfer(TransferCreateDto createDto, UUID publicUserId) {
            log.debug("Creating transfer: from id = {}, to id ={}, amount = {}",
                    createDto.fromAccountPublicId(), createDto.toAccountPublicId(), createDto.amount());

            UUID fromAccountPublicId = createDto.fromAccountPublicId();
            UUID toAccountPublicId = createDto.toAccountPublicId();

            if(fromAccountPublicId.equals(toAccountPublicId)) {
                log.warn("Self-transfer attempt from account: {}", fromAccountPublicId);
                throw new SameAccountTransferException(fromAccountPublicId);
            }

            // Find accounts for the transfer
            Account fromAccount = accountRepository.findByPublicIdAndUserId(fromAccountPublicId, publicUserId)
                    .orElseThrow(
                        () -> {
                            log.warn("From account not found: publicId={}, userId={}", fromAccountPublicId, publicUserId);
                            return new AccountNotFoundException("'from account' does not exist");
                        });

            Account toAccount = accountRepository.findByPublicId(toAccountPublicId)
                    .orElseThrow(() -> {
                            log.warn("To account not found: publicId={}", toAccountPublicId);
                            return new AccountNotFoundException("'to account' does not exist");
                    });

            // Creating PENDING transfer not to hold the http thread and process the transfer independently
            Transfer transfer = Transfer.builder()
                    .fromAccount(fromAccount)
                    .toAccount(toAccount)
                    .amount(createDto.amount())
                    .status(TransferStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .build();

            Transfer savedTransfer = transferRepository.save(transfer);

            log.info("Created pending transfer: publicId={}, amount={}, fromAccount={}, toAccount={}",
                    savedTransfer.getPublicId(), savedTransfer.getAmount(),
                    fromAccountPublicId, toAccountPublicId);

            TransferReadDto savedTransferReadDto = transferMapper.mapToReadDto(savedTransfer);

            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {

                        @Override
                        public void afterCommit() {
                            log.debug("Transaction committed for transfer: {}", savedTransfer.getPublicId());

                            String key = publicUserId + "_" + savedTransferReadDto.publicId();
                            cacheService.put("transfers", key, savedTransferReadDto);

                            try {
                                // Sending to RabbitMQ
                                rabbitTemplate.convertAndSend(
                                        EXCHANGE_NAME,
                                        ROUTING_KEY,
                                        new TransferAddedEvent(
                                                publicUserId,
                                                savedTransfer.getPublicId(),
                                                fromAccount.getId(),
                                                toAccount.getId(),
                                                savedTransfer.getId()
                                        ));
                                log.info("TransferAddedEvent sent to RabbitMQ: transferId={}", savedTransfer.getPublicId());
                            } catch (Exception e) {
                                log.error("Failed to send TransferAddedEvent for transfer {} — transfer will stay PENDING",
                                        savedTransfer.getPublicId(), e);

                                // Here outbox pattern can be added
                            }
                        }
                    }
            );
            return savedTransferReadDto;
        }

        @Cacheable(cacheNames = "transfers", key = "#userPublicId.toString() + '_' + #transferPublicId.toString()")
        public TransferReadDto getTransferStatus(UUID transferPublicId, UUID userPublicId) {

            log.debug("Getting transfer status: transferId={}, userId={}", transferPublicId, userPublicId);


            Transfer transfer = transferRepository
                    .findByTransferPublicId_and_UserPublicId(transferPublicId, userPublicId)
                    .orElseThrow(() -> {
                        log.warn("Transfer not found: publicId={}, userId={}", transferPublicId, userPublicId);
                        return new TransferNotFoundException(transferPublicId);
                    });

            log.debug("Transfer found: status={}", transfer.getStatus());
            return transferMapper.mapToReadDto(transfer);
        }

        public Page<TransferReadDto> getTransfersByUserId(UUID userId,  int page, int size) {

            log.debug("Getting transfers for user: {}, page: {}, size: {}", userId, page, size);

            PageRequest pageRequest = PageRequest.of(page, size);
            Page<TransferProjection> pageResult = transferRepository
                    .findByUserIdOrderByCreatedAtDesc(userId, pageRequest);

            return pageResult.map(TransferReadDto::fromProjection);
        }


        // Not a main feature, no enpoints were added yet
        public List<AdminSenderLeaderboardDto> getSenderLeaderboard(int leaderboardLimit, int daysCount) {

            log.debug("Getting sender leaderboard: limit={}, days={}", leaderboardLimit, daysCount);

            List<AdminSenderLeaderboardDto> result = transferRepository
                    .findTopUsersByTransferCount(leaderboardLimit, daysCount)
                    .stream()
                    .map(AdminSenderLeaderboardDto::fromProjection)
                    .toList();

            log.info("Leaderboard generated: {} entries for last {} days", result.size(), daysCount);
            return result;
        }
    }