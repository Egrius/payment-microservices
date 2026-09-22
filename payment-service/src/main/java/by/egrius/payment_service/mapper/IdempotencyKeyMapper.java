package by.egrius.payment_service.mapper;


import by.egrius.payment_service.dto.idempotency_key.IdempotencyKeyCreateDto;
import by.egrius.payment_service.dto.idempotency_key.IdempotencyKeyReadDto;
import by.egrius.payment_service.entity.IdempotencyKey;
import org.mapstruct.*;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface IdempotencyKeyMapper {

    IdempotencyKeyReadDto toReadDto(IdempotencyKey idempotencyKey);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "responseStatus", ignore = true)
    @Mapping(target = "responseBody", ignore = true)
    @Mapping(target = "errorResponse", ignore = true)
    @Mapping(target = "expiresAt", ignore = true)
    IdempotencyKey fromCreateDto(IdempotencyKeyCreateDto createDto);
}
