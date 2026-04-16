package com.qcomm.engine.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "route_edges")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_store_id", nullable = false)
    private DarkStore sourceStore;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_store_id", nullable = false)
    private DarkStore targetStore;

    @Column(name = "base_weight", nullable = false)
    private Integer baseWeight;

    @Column(name = "current_ai_weight", nullable = false)
    private Integer currentAiWeight;
}

