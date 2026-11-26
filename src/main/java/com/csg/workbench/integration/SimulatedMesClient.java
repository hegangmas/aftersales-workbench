package com.csg.workbench.integration;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MES 生产系统模拟实现。
 */
@Component
@ConditionalOnProperty(name = "workbench.integration.mode", havingValue = "simulated", matchIfMissing = true)
public class SimulatedMesClient implements MesClient {

    @Override
    public MesResult queryDevice(String sn, Map<String, Object> localRow, boolean simulateFailure, String queriedAt) {
        String ts = SourceStatus.resolveQueriedAt(queriedAt);
        if (simulateFailure) {
            return MesResult.degraded(sn, "超时降级，已标注缺口", ts);
        }
        Object manufacturedAt = localRow == null ? null : localRow.get("manufacturedAt");
        Object mesBatch = localRow == null ? null : localRow.get("mesBatch");
        Object mesTestResult = localRow == null ? null : localRow.get("mesTestResult");
        return MesResult.success(sn, manufacturedAt, mesBatch, mesTestResult, "已同步（模拟）", ts);
    }
}
