package by.egrius.payment_service.event;

import by.egrius.payment_service.dto.transfer.TransferReadDto;
import by.egrius.payment_service.entity.TransferStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class TransferProcessedEvent {

    private UUID userPublicTd;
    private BigDecimal amount;
    private UUID fromAccountPublicId;
    private UUID toAccountPublicId;
    private UUID transferPublicId;
    private TransferStatus status;

    public TransferProcessedEvent(UUID userPublicTd,
                                  UUID fromAccountPublicId, UUID toAccountPublicId,
                                  UUID transferPublicId, BigDecimal amount,
                                  TransferStatus status
    ) {

        this.userPublicTd = userPublicTd;
        this.fromAccountPublicId = fromAccountPublicId;
        this.toAccountPublicId = toAccountPublicId;
        this.transferPublicId = transferPublicId;
        this.amount = amount;
        this.status = status;
    }

    public TransferProcessedEvent(UUID userPublicTd, TransferReadDto transferReadDto) {

        this.userPublicTd = userPublicTd;
        this.toAccountPublicId = transferReadDto.toAccountPublicId();
        this.fromAccountPublicId = transferReadDto.fromAccountPublicId();
        this.transferPublicId = transferReadDto.publicId();
        this.amount = transferReadDto.amount();
        this.status = transferReadDto.status();
    }
}
