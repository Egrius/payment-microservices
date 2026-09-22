package by.egrius.payment_service.service;

import by.egrius.payment_service.dto.idempotency_key.IdempotencyKeyCreateDto;
import by.egrius.payment_service.dto.idempotency_key.IdempotencyKeyReadDto;
import by.egrius.payment_service.dto.transfer.TransferReadDto;
import by.egrius.payment_service.entity.IdempotencyKey;
import by.egrius.payment_service.exception.handler.ErrorResponse;
import by.egrius.payment_service.mapper.IdempotencyKeyMapper;
import by.egrius.payment_service.repository.IdempotencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRepository idempotencyRepository;
    private final IdempotencyKeyMapper idempotencyKeyMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyKeyReadDto complete(UUID idempotencyValue, UUID userId,
                                          int responseStatus,
                                          TransferReadDto transferReadDto,
                                          ErrorResponse errorResponse) {

        IdempotencyKey key = idempotencyRepository.findByValueAndUserId(idempotencyValue, userId)
                .orElseThrow();

        key.setResponseStatus(responseStatus);
        key.setResponseBody(transferReadDto);
        key.setErrorResponse(errorResponse);

        return idempotencyKeyMapper.toReadDto(idempotencyRepository.save(key));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReserveResult reserve(IdempotencyKeyCreateDto createDto) {

        int inserted = idempotencyRepository.tryInsert(
                createDto.value(),
                createDto.userId(),
                createDto.requestHash(),
                createDto.createdAt(),
                createDto.createdAt().plus(createDto.ttlAmount(), createDto.ttlTimeUnit().toChronoUnit())
        );

        IdempotencyKey key = idempotencyRepository
                .findByValueAndUserId(createDto.value(), createDto.userId())
                .orElseThrow(() -> new IllegalStateException("Key not found after tryInsert"));

        return new ReserveResult(idempotencyKeyMapper.toReadDto(key), inserted == 1);
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean release(UUID idempotencyValue, UUID userId) {

        return idempotencyRepository.deleteByValueAndUserId(idempotencyValue, userId);
    }

}
