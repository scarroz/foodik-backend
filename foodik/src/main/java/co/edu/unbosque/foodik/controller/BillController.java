package co.edu.unbosque.foodik.controller;

import co.edu.unbosque.foodik.domain.dto.request.*;
import co.edu.unbosque.foodik.domain.dto.response.*;
import co.edu.unbosque.foodik.exception.ApiResponse;
import co.edu.unbosque.foodik.service.BillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bills")
@Tag(name = "Bills")
public class BillController {

    private final BillService billService;
    public BillController(BillService billService) { this.billService = billService; }

    @PostMapping
    @Operation(summary = "Create a bill for a reservation")
    public ResponseEntity<ApiResponse<BillResponse>> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateBillRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Bill created", billService.create(request, userDetails.getUsername())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get bill by ID")
    public ResponseEntity<ApiResponse<BillResponse>> findById(
            @PathVariable UUID id, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(billService.findById(id, userDetails.getUsername())));
    }

    @PostMapping("/{id}/split")
    @Operation(summary = "Split the bill (EQUAL, INDIVIDUAL or CHAINED)")
    public ResponseEntity<ApiResponse<BillSummaryResponse>> split(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SplitBillRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Bill split calculated",
                billService.split(id, request, userDetails.getUsername())));
    }

    @GetMapping("/{id}/summary")
    @Operation(summary = "Get current payment summary")
    public ResponseEntity<ApiResponse<BillSummaryResponse>> getSummary(
            @PathVariable UUID id, @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(billService.getSummary(id, userDetails.getUsername())));
    }
}
