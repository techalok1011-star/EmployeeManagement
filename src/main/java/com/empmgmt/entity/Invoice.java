package com.empmgmt.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoices", indexes = {
        @Index(columnList = "party_name"),
        @Index(columnList = "invoice_number", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 100)
    private String invoiceNumber;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    /**
     * Matches Party.combined (name + '_' + gstin, or just name).
     * Denormalised string — same pattern used by PaymentEntry.partyName.
     */
    @Column(name = "party_name", nullable = false, length = 700)
    private String partyName;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    /** Number of cement bags sold - amount is derived as bags * ratePerBag. Null for pre-existing/imported invoices. */
    @Column(name = "bags")
    private Integer bags;

    /** Rate per bag (INR) on the invoice date. Null for pre-existing/imported invoices. */
    @Column(name = "rate_per_bag", precision = 10, scale = 2)
    private BigDecimal ratePerBag;

    @Column(length = 1000)
    private String description;

    /** How the cement physically left / was collected for this invoice. */
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_mode", nullable = false, length = 20)
    private DeliveryMode deliveryMode;

    /** Truck/trolley registration number, or the collecting party's vehicle number — optional. */
    @Column(name = "transport_number", length = 50)
    private String transportNumber;

    /** Voucher number from the source Sales Register (e.g. Tally), if this invoice was imported. */
    @Column(name = "sales_vch_no", length = 20)
    private String salesVchNo;

    /** Username of the ADMIN/ACCOUNTANT/MANAGER who added this invoice. Null for pre-existing/imported invoices. */
    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum DeliveryMode {
        TRUCK("Truck"),
        SELF_PICKUP("Self Pickup"),
        TROLLEY("Trolley");

        private final String displayName;

        DeliveryMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
