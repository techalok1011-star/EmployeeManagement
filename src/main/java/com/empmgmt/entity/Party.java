package com.empmgmt.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "parties", indexes = {@Index(columnList = "combined")})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Party {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 500)
    private String name;

    @Column(name = "gst", length = 128)
    private String gst;

    @Column(name = "combined", length = 700, unique = true)
    private String combined; // name + '_' + gst or name

    /** Trailing numeric ledger code from the source accounting system (e.g. Tally), if any. */
    @Column(name = "trailing_number", length = 20)
    private String trailingNumber;

    /** Total sales amount from Excel (sum across all invoices for this party) */
    @Column(name = "total_amount", precision = 18, scale = 2)
    private BigDecimal totalAmount;

    /** WhatsApp phone number in E.164 format, e.g. 919876543210 */
    @Column(name = "phone", length = 20)
    private String phone;

    /** Whether this party has opted in to receive WhatsApp reminders */
    @Column(name = "whatsapp_opt_in", nullable = false, columnDefinition = "BOOLEAN NOT NULL DEFAULT FALSE")
    private boolean whatsappOptIn = false;
}

