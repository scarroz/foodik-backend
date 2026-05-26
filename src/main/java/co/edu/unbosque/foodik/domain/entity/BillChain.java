package co.edu.unbosque.foodik.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "bill_chains", indexes = {
        @Index(name = "idx_chain_bill", columnList = "bill_id"),
        @Index(name = "idx_chain_payer", columnList = "payer_id")
})
public class BillChain {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bill_id", nullable = false)
    private Bill bill;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payer_id", nullable = false)
    private User payer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beneficiary_id", nullable = false)
    private User beneficiary;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    public BillChain() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Bill getBill() { return bill; }
    public void setBill(Bill bill) { this.bill = bill; }

    public User getPayer() { return payer; }
    public void setPayer(User payer) { this.payer = payer; }

    public User getBeneficiary() { return beneficiary; }
    public void setBeneficiary(User beneficiary) { this.beneficiary = beneficiary; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
