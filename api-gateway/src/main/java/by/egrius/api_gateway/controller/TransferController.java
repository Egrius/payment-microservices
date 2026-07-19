package by.egrius.api_gateway.controller;

import by.egrius.api_gateway.dto.transfer.TransferCreateDto;
import by.egrius.api_gateway.dto.transfer.TransferReadDto;
import by.egrius.api_gateway.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @PostMapping
    public ResponseEntity<TransferReadDto> createTransfer(@Validated @RequestBody TransferCreateDto createDto) {
        TransferReadDto transfer = transferService.createTransfer(createDto);
        return ResponseEntity
                .created(URI.create("/api/transfers/" + transfer.transferId()))
                .body(transfer);
    }

    @GetMapping("/{id}")
    public TransferReadDto getTransferStatus(@PathVariable Long id) {
        return transferService.getTransferStatus(id);
    }
}