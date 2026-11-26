package com.csg.workbench.integration;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 售后安装系统模拟实现。
 */
@Component
@ConditionalOnProperty(name = "workbench.integration.mode", havingValue = "simulated", matchIfMissing = true)
public class SimulatedInstallClient implements InstallClient {

    @Override
    public InstallResult queryInstall(String sn, Map<String, Object> localRow, boolean simulateFailure, String queriedAt) {
        String ts = SourceStatus.resolveQueriedAt(queriedAt);
        if (simulateFailure) {
            return InstallResult.degraded("暂不可用", ts);
        }
        Object installedAt = localRow == null ? null : localRow.get("installedAt");
        return InstallResult.success(installedAt, "已同步（模拟）", ts);
    }
}
