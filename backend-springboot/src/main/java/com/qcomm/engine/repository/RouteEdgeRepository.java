package com.qcomm.engine.repository;

import com.qcomm.engine.model.RouteEdge;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteEdgeRepository extends JpaRepository<RouteEdge, Long> {

    @Query("""
            select edge
            from RouteEdge edge
            join fetch edge.sourceStore
            join fetch edge.targetStore
            """)
    List<RouteEdge> findAllWithStores();
}
