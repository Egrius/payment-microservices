package by.egrius.payment_service.mapper;

import by.egrius.payment_service.dto.account.AccountReadDto;
import by.egrius.payment_service.entity.Account;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    @Mapping(source = "publicId", target = "publicId")
    AccountReadDto toReadDto(Account account);
}