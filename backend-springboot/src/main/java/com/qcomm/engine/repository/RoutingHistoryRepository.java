package com.qcomm.engine.repository;

import com.qcomm.engine.model.RoutingHistory;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutingHistoryRepository extends JpaRepository<RoutingHistory, Long> {

    List<RoutingHistory> findTop100ByOrderByTimestampDesc();

    @Query("""
            select
                history.algorithmUsed as algorithm,
                count(history) as runCount,
                avg(history.executionTimeMs) as avgExecutionTimeMs,
                avg(history.totalCost) as avgTotalCost
            from RoutingHistory history
            group by history.algorithmUsed
            """)
    List<HistoryAlgorithmProjection> summarizeByAlgorithm();

    interface HistoryAlgorithmProjection {
        String getAlgorithm();

        Long getRunCount();

        Double getAvgExecutionTimeMs();

        Double getAvgTotalCost();
    }
}
