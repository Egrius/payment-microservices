package by.egrius.payment_service.controller.api;

import by.egrius.payment_service.annotation.CurrentUser;
import by.egrius.payment_service.dto.response.ErrorResponse;
import by.egrius.payment_service.dto.transfer.AdminSenderLeaderboardDto;
import by.egrius.payment_service.dto.transfer.TransferCreateDto;
import by.egrius.payment_service.dto.transfer.TransferReadDto;
import by.egrius.payment_service.dto.user.CurrentUserDto;
import by.egrius.payment_service.repository.filter.TransferFilter;
import by.egrius.payment_service.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.springframework.data.domain.Sort.Direction.DESC;

@Tag(name = "Transfers", description = "Transfer operations")
@SecurityRequirement(name = "bearer-jwt")
@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
@Validated
public class TransferController {

    private final TransferService transferService;

    @Operation(summary = "Create transfer",
            description = "Creates PENDING transfer and sends the event into RabbitMQ")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transfer created", content = @Content(schema = @Schema(implementation = TransferReadDto.class))),
            @ApiResponse(responseCode = "400", description = "Validation exception", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid JWT", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Idempotency conflict", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<TransferReadDto> createTransfer(@CurrentUser CurrentUserDto currentUserDto,

                                                          @Valid @RequestBody TransferCreateDto createDto,

                                                          @Parameter(description = "Idempotency key (UUID)", required = true)
                                                          @RequestHeader("Idempotency-Key") UUID idempotencyKey) {


        TransferReadDto transfer = transferService.createTransfer(createDto, currentUserDto.publicId(), idempotencyKey);

        return ResponseEntity
                .created(URI.create("/api/transfers/" + transfer.publicId()))
                .body(transfer);
    }

    @Operation(summary = "Get transfers by filter",
            description = "Returns paginated list of transfers with optional filters (status, amount range, date range)")
    @ApiResponse(responseCode = "200", description = "Page of transfers")
    @GetMapping
    public Page<TransferReadDto> getTransfersByFilter(@CurrentUser CurrentUserDto currentUserDto,
                                                      @ModelAttribute TransferFilter filter,
                                                      @PageableDefault(size = 20, sort = "createdAt", direction = DESC) Pageable pageable) {

        return transferService.getTransfersByFilter(currentUserDto.publicId(), filter, pageable);
    }

    @Operation(summary = "Get transfer status by public ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transfer found"),
            @ApiResponse(responseCode = "404", description = "Transfer not found", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{transfer-public-id}")
    public TransferReadDto getTransferStatus(@Parameter(description = "Transfer public ID", required = true)
                                             @PathVariable("transfer-public-id") UUID transferId,

                                             @CurrentUser CurrentUserDto currentUserDto) {

        return transferService.getTransferStatus(transferId, currentUserDto.publicId());
    }


    @Operation(summary = "Get sender leaderboard",
            description = "Admin only. Top senders by transfer count for the given period.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Leaderboard entries"),
            @ApiResponse(responseCode = "403", description = "User is not admin", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/leaderboard")
    public List<AdminSenderLeaderboardDto> getSenderLeaderboard(
                                            @RequestParam(defaultValue = "10") @Min(1) @Max(1000)
                                            @Parameter(description = "Maximum number of leaderboard entries", example = "10")
                                            int leaderboardLimit,

                                            @RequestParam(defaultValue = "30") @Min(1)
                                            @Parameter(description = "Number of days to look back", example = "30")
                                            int daysCount) {

        return transferService.getSenderLeaderboard(leaderboardLimit, daysCount);
    }
}