package by.egrius.api_gateway.mapper;

import by.egrius.api_gateway.dto.transfer.TransferReadDto;
import by.egrius.api_gateway.entity.Transfer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TransferMapper {

    @Mapping(target = "transferId", source = "publicId")
    @Mapping(target = "fromAccountId", source = "fromAccount.publicId")
    @Mapping(target = "toAccountId", source = "toAccount.publicId")
    TransferReadDto mapToReadDto(Transfer transfer);
}
