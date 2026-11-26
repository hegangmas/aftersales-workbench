package com.csg.workbench.integration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 400 客服系统模拟实现。
 */
@Component
@ConditionalOnProperty(name = "workbench.integration.mode", havingValue = "simulated", matchIfMissing = true)
public class SimulatedCallCenterClient implements CallCenterClient {

    @Override
    public CallCenterResult queryCallHistory(String sn, String customerPhone, boolean simulateFailure, String queriedAt) {
        String ts = SourceStatus.resolveQueriedAt(queriedAt);
        if (simulateFailure) {
            return CallCenterResult.degraded("暂不可用", ts);
        }
        return CallCenterResult.success(sampleRecordings(sn), sampleCall(customerPhone), "已同步（模拟）", ts);
    }

    private static List<Map<String, Object>> sampleRecordings(String sn) {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        Map<String, Object> r1 = new LinkedHashMap<String, Object>();
        r1.put("id", "REC-" + sn + "-01");
        r1.put("time", "11-25 10:22");
        r1.put("title", "通话录音");
        r1.put("duration", "00:45");
        list.add(r1);
        Map<String, Object> r2 = new LinkedHashMap<String, Object>();
        r2.put("id", "REC-" + sn + "-02");
        r2.put("time", "11-25 10:15");
        r2.put("title", "通话录音");
        r2.put("duration", "03:28");
        list.add(r2);
        return list;
    }

    private static Map<String, Object> sampleCall(String phone) {
        Map<String, Object> call = new LinkedHashMap<String, Object>();
        call.put("callId", "CALL-400-SIM-01");
        call.put("caller", phone == null ? "138****1234" : phone);
        call.put("duration", "00:22");
        call.put("recordingStatus", "录音中");
        return call;
    }
}
