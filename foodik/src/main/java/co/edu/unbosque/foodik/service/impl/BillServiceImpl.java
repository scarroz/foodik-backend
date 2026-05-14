package co.edu.unbosque.foodik.service.impl;

import co.edu.unbosque.foodik.domain.dto.request.BillItemRequest;
import co.edu.unbosque.foodik.domain.dto.request.CreateBillRequest;
import co.edu.unbosque.foodik.domain.dto.request.SplitBillRequest;
import co.edu.unbosque.foodik.domain.dto.response.BillItemResponse;
import co.edu.unbosque.foodik.domain.dto.response.BillResponse;
import co.edu.unbosque.foodik.domain.dto.response.BillSummaryResponse;
import co.edu.unbosque.foodik.domain.entity.Bill;
import co.edu.unbosque.foodik.domain.entity.BillItem;
import co.edu.unbosque.foodik.domain.entity.MenuItem;
import co.edu.unbosque.foodik.domain.entity.User;
import co.edu.unbosque.foodik.domain.enums.SplitMode;
import co.edu.unbosque.foodik.exception.ConflictException;
import co.edu.unbosque.foodik.exception.ResourceNotFoundException;
import co.edu.unbosque.foodik.exception.UnauthorizedException;
import co.edu.unbosque.foodik.repository.BillRepository;
import co.edu.unbosque.foodik.repository.MenuItemRepository;
import co.edu.unbosque.foodik.repository.ReservationRepository;
import co.edu.unbosque.foodik.repository.UserRepository;
import co.edu.unbosque.foodik.service.BillService;
import co.edu.unbosque.foodik.service.strategy.BillSplitStrategy;
import co.edu.unbosque.foodik.service.strategy.ChainedSplitStrategy;
import co.edu.unbosque.foodik.service.strategy.EqualSplitStrategy;
import co.edu.unbosque.foodik.service.strategy.IndividualSplitStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class BillServiceImpl implements BillService {

    private final BillRepository billRepository;
    private final ReservationRepository reservationRepository;
    private final MenuItemRepository menuItemRepository;
    private final UserRepository userRepository;
    private final EqualSplitStrategy equalSplitStrategy;
    private final IndividualSplitStrategy individualSplitStrategy;
    private final ChainedSplitStrategy chainedSplitStrategy;

    public BillServiceImpl(BillRepository billRepository,
                            ReservationRepository reservationRepository,
                            MenuItemRepository menuItemRepository,
                            UserRepository userRepository,
                            EqualSplitStrategy equalSplitStrategy,
                            IndividualSplitStrategy individualSplitStrategy,
                            ChainedSplitStrategy chainedSplitStrategy) {
        this.billRepository = billRepository;
        this.reservationRepository = reservationRepository;
        this.menuItemRepository = menuItemRepository;
        this.userRepository = userRepository;
        this.equalSplitStrategy = equalSplitStrategy;
        this.individualSplitStrategy = individualSplitStrategy;
        this.chainedSplitStrategy = chainedSplitStrategy;
    }

    @Override
    @Transactional
    public BillResponse create(CreateBillRequest request, String userEmail) {
        var reservation = reservationRepository.findById(request.reservationId())
                .orElseThrow(() -> new ResourceNotFoundException("Reservation", request.reservationId()));

        if (!reservation.getUser().getEmail().equals(userEmail))
            throw new UnauthorizedException("You can only create bills for your own reservations");

        if (billRepository.findByReservationId(reservation.getId()).isPresent())
            throw new ConflictException("A bill already exists for this reservation");

        Bill bill = new Bill();
        bill.setReservation(reservation);
        bill.setSplitMode(request.splitMode());
        bill.setTotalAmount(BigDecimal.ZERO);
        bill.setItems(new ArrayList<>());
        bill.setChains(new ArrayList<>());
        bill = billRepository.save(bill);

        BigDecimal total = BigDecimal.ZERO;
        for (BillItemRequest itemReq : request.items()) {
            MenuItem menuItem = menuItemRepository.findById(itemReq.menuItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("MenuItem", itemReq.menuItemId()));

            User assignedUser = null;
            if (itemReq.assignedUserId() != null) {
                assignedUser = userRepository.findById(itemReq.assignedUserId())
                        .orElseThrow(() -> new ResourceNotFoundException("User", itemReq.assignedUserId()));
            }

            BigDecimal subtotal = menuItem.getPrice().multiply(BigDecimal.valueOf(itemReq.quantity()));
            total = total.add(subtotal);

            BillItem item = new BillItem();
            item.setBill(bill);
            item.setMenuItem(menuItem);
            item.setAssignedUser(assignedUser);
            item.setQuantity(itemReq.quantity());
            item.setSubtotal(subtotal);
            bill.getItems().add(item);
        }

        bill.setTotalAmount(total);
        bill = billRepository.save(bill);
        return toResponse(bill);
    }

    @Override
    @Transactional(readOnly = true)
    public BillResponse findById(UUID id, String userEmail) {
        return toResponse(getOwnedBill(id, userEmail));
    }

    @Override
    @Transactional
    public BillSummaryResponse split(UUID billId, SplitBillRequest request, String userEmail) {
        Bill bill = getOwnedBill(billId, userEmail);
        bill.setSplitMode(request.splitMode());
        billRepository.save(bill);
        return resolveStrategy(request.splitMode()).split(bill, request);
    }

    @Override
    @Transactional(readOnly = true)
    public BillSummaryResponse getSummary(UUID billId, String userEmail) {
        Bill bill = getOwnedBill(billId, userEmail);
        SplitBillRequest syntheticRequest = new SplitBillRequest(bill.getSplitMode(), null, null);
        return resolveStrategy(bill.getSplitMode()).split(bill, syntheticRequest);
    }

    private Bill getOwnedBill(UUID billId, String userEmail) {
        Bill bill = billRepository.findById(billId)
                .orElseThrow(() -> new ResourceNotFoundException("Bill", billId));
        if (!bill.getReservation().getUser().getEmail().equals(userEmail))
            throw new UnauthorizedException("Access denied to this bill");
        return bill;
    }

    private BillSplitStrategy resolveStrategy(SplitMode mode) {
        return switch (mode) {
            case EQUAL -> equalSplitStrategy;
            case INDIVIDUAL -> individualSplitStrategy;
            case CHAINED -> chainedSplitStrategy;
        };
    }

    private BillResponse toResponse(Bill bill) {
        List<BillItemResponse> items = bill.getItems().stream()
                .map(i -> new BillItemResponse(
                        i.getId(),
                        i.getMenuItem().getId(),
                        i.getMenuItem().getName(),
                        i.getAssignedUser() != null ? i.getAssignedUser().getId() : null,
                        i.getQuantity(),
                        i.getSubtotal()))
                .toList();
        return new BillResponse(bill.getId(), bill.getReservation().getId(),
                bill.getTotalAmount(), bill.getSplitMode(), bill.getStatus(), items);
    }
}
