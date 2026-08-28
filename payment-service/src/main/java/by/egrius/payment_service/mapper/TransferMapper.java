package by.egrius.payment_service.mapper;

import by.egrius.payment_service.dto.transfer.TransferReadDto;
import by.egrius.payment_service.entity.Transfer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface TransferMapper {

    @Mapping(target = "publicId", source = "publicId")
    @Mapping(target = "fromAccountPublicId", source = "fromAccount.publicId")
    @Mapping(target = "toAccountPublicId", source = "toAccount.publicId")
    TransferReadDto mapToReadDto(Transfer transfer);

}
