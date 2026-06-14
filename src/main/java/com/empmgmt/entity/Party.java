package com.empmgmt.entity;

import jakarta.persistence.*;
import lombok.*;

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
}

