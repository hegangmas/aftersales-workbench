package com.csg.workbench.integration;

import java.util.Map;

/**
 * MES 生产系统集成客户端接口。
 */
public interface MesClient {

    String SOURCE_NAME = "MES生产系统";

    /**
     * 查询设备在 MES 系统的生产批次与出厂测试信息。
     */
    MesResult queryDevice(String sn, Map<String, Object> localRow, boolean simulateFailure, String queriedAt);

    final class MesResult {
        private final Object manufacturedAt;
        private final Object mesBatch;
        private final Object mesTestResult;
        private final String traceDeepLink;
        private final Map<String, Object> sourceStatus;

        private MesResult(Object manufacturedAt, Object mesBatch, Object mesTestResult,
                          String traceDeepLink, Map<String, Object> sourceStatus) {
            this.manufacturedAt = manufacturedAt;
            this.mesBatch = mesBatch;
            this.mesTestResult = mesTestResult;
            this.traceDeepLink = traceDeepLink;
            this.sourceStatus = sourceStatus;
        }

        public static MesResult success(String sn, Object manufacturedAt, Object mesBatch,
                                        Object mesTestResult, String note, String queriedAt) {
            return new MesResult(
                manufacturedAt,
                mesBatch,
                mesTestResult,
                buildTraceDeepLink(sn),
                SourceStatus.of(SOURCE_NAME, true, note, queriedAt));
        }

        public static MesResult degraded(String sn, String note, String queriedAt) {
            return new MesResult(
                null,
                null,
                "MES接口暂不可用（降级）",
                buildTraceDeepLink(sn),
                SourceStatus.of(SOURCE_NAME, false, note, queriedAt));
        }

        private static String buildTraceDeepLink(String sn) {
            return "https://mes.internal/trace/" + sn;
        }

        public Object getManufacturedAt() {
            return manufacturedAt;
        }

        public Object getMesBatch() {
            return mesBatch;
        }

        public Object getMesTestResult() {
            return mesTestResult;
        }

        public String getTraceDeepLink() {
            return traceDeepLink;
        }

        public Map<String, Object> getSourceStatus() {
            return sourceStatus;
        }
    }
}
