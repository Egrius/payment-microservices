package by.egrius.api_gateway.controller.api;

import by.egrius.api_gateway.annotation.CurrentUser;
import by.egrius.api_gateway.dto.transfer.TransferCreateDto;
import by.egrius.api_gateway.dto.transfer.TransferReadDto;
import by.egrius.api_gateway.dto.user.CurrentUserDto;
import by.egrius.api_gateway.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping("/create")
    public ResponseEntity<TransferReadDto> createTransfer(@CurrentUser CurrentUserDto currentUserDto,
                                                          @Validated @RequestBody TransferCreateDto createDto) {

        TransferReadDto transfer = transferService.createTransfer(createDto, currentUserDto.publicId());

        return ResponseEntity
                .created(URI.create("/api/transfers/" + transfer.transferId()))
                .body(transfer);
    }

    @GetMapping("/{transfer-public-id}")
    public TransferReadDto getTransferStatus(@PathVariable UUID transferId,
                                             @CurrentUser CurrentUserDto currentUserDto) {

        return transferService.getTransferStatus(transferId, currentUserDto.publicId());
    }
}