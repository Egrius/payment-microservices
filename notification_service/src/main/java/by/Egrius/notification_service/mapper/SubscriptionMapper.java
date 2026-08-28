package by.Egrius.notification_service.mapper;

import by.Egrius.notification_service.dto.SubscriptionReadDto;
import by.Egrius.notification_service.entity.Subscription;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {
    SubscriptionReadDto toReadDto(Subscription subscription);
}
