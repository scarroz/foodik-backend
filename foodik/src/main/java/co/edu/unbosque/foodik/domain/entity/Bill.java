package co.edu.unbosque.foodik.domain.entity;

import co.edu.unbosque.foodik.domain.enums.BillStatus;
import co.edu.unbosque.foodik.domain.enums.SplitMode;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "bills")
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false, unique = true)
    private Reservation reservation;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private SplitMode splitMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BillStatus status = BillStatus.PENDING;

    @OneToMany(mappedBy = "bill", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BillItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "bill", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<BillChain> chains = new ArrayList<>();

    public Bill() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Reservation getReservation() { return reservation; }
    public void setReservation(Reservation reservation) { this.reservation = reservation; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public SplitMode getSplitMode() { return splitMode; }
    public void setSplitMode(SplitMode splitMode) { this.splitMode = splitMode; }

    public BillStatus getStatus() { return status; }
    public void setStatus(BillStatus status) { this.status = status; }

    public List<BillItem> getItems() { return items; }
    public void setItems(List<BillItem> items) { this.items = items; }

    public List<BillChain> getChains() { return chains; }
    public void setChains(List<BillChain> chains) { this.chains = chains; }
}
