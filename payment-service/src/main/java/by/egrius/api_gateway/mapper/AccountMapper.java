package by.egrius.api_gateway.mapper;

import by.egrius.api_gateway.dto.account.AccountReadDto;
import by.egrius.api_gateway.entity.Account;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    @Mapping(source = "publicId", target = "publicId")
    AccountReadDto toReadDto(Account account);
}