package com.csg.workbench.integration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 智享乐装系统模拟实现。
 */
@Component
@ConditionalOnProperty(name = "workbench.integration.mode", havingValue = "simulated", matchIfMissing = true)
public class SimulatedLezhuangClient implements LezhuangClient {

    @Override
    public LezhuangResult queryServiceRecords(String sn, String projectCode, boolean simulateFailure, String queriedAt) {
        String ts = SourceStatus.resolveQueriedAt(queriedAt);
        if (simulateFailure) {
            return LezhuangResult.degraded(projectCode, "暂不可用", ts);
        }
        return LezhuangResult.success(projectCode, sampleRepairs(sn), "已同步（模拟）", ts);
    }

    private static List<Map<String, Object>> sampleRepairs(String sn) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        Map<String, Object> r1 = new LinkedHashMap<String, Object>();
        r1.put("date", "2026-10-12");
        r1.put("orderNo", "LZ-20261012-09");
        r1.put("summary", "更换 4G 通信模块");
        r1.put("source", "智享乐装（模拟）");
        r1.put("deepLink", "#lezhuang/LZ-20261012-09");
        list.add(r1);
        Map<String, Object> r2 = new LinkedHashMap<String, Object>();
        r2.put("date", "2026-09-05");
        r2.put("orderNo", "RM-20260905-03");
        r2.put("summary", "远程升级主板程序（" + sn + "）");
        r2.put("source", "远程运维（模拟）");
        r2.put("deepLink", "#charger-platform/" + sn);
        list.add(r2);
        return list;
    }
}
