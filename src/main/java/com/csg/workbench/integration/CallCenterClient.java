package com.csg.workbench.integration;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 400 客服系统集成客户端接口。
 */
public interface CallCenterClient {

    String SOURCE_NAME = "400客服系统";

    /**
     * 查询通话录音与当前模拟通话状态。
     */
    CallCenterResult queryCallHistory(String sn, String customerPhone, boolean simulateFailure, String queriedAt);

    final class CallCenterResult {
        private final List<Map<String, Object>> callRecordings;
        private final Map<String, Object> simulatedCall;
        private final Map<String, Object> sourceStatus;

        private CallCenterResult(List<Map<String, Object>> callRecordings,
                                 Map<String, Object> simulatedCall,
                                 Map<String, Object> sourceStatus) {
            this.callRecordings = callRecordings;
            this.simulatedCall = simulatedCall;
            this.sourceStatus = sourceStatus;
        }

        public static CallCenterResult success(List<Map<String, Object>> callRecordings,
                                               Map<String, Object> simulatedCall,
                                               String note,
                                               String queriedAt) {
            List<Map<String, Object>> safeList = callRecordings == null
                ? Collections.<Map<String, Object>>emptyList()
                : callRecordings;
            return new CallCenterResult(
                safeList,
                simulatedCall,
                SourceStatus.of(SOURCE_NAME, true, note, queriedAt));
        }

        public static CallCenterResult degraded(String note, String queriedAt) {
            return new CallCenterResult(
                Collections.<Map<String, Object>>emptyList(),
                null,
                SourceStatus.of(SOURCE_NAME, false, note, queriedAt));
        }

        public List<Map<String, Object>> getCallRecordings() {
            return callRecordings;
        }

        public Map<String, Object> getSimulatedCall() {
            return simulatedCall;
        }

        public Map<String, Object> getSourceStatus() {
            return sourceStatus;
        }
    }
}
