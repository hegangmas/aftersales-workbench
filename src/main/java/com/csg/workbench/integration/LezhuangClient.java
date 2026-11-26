package com.csg.workbench.integration;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 智享乐装现场单与历史维保系统集成客户端接口。
 */
public interface LezhuangClient {

    String SOURCE_NAME = "智享乐装";

    /**
     * 查询设备在智享乐装的历史维修记录及安装单深链。
     */
    LezhuangResult queryServiceRecords(String sn, String projectCode, boolean simulateFailure, String queriedAt);

    final class LezhuangResult {
        private final List<Map<String, Object>> repairHistory;
        private final String installOrderDeepLink;
        private final Map<String, Object> sourceStatus;

        private LezhuangResult(List<Map<String, Object>> repairHistory,
                               String installOrderDeepLink,
                               Map<String, Object> sourceStatus) {
            this.repairHistory = repairHistory;
            this.installOrderDeepLink = installOrderDeepLink;
            this.sourceStatus = sourceStatus;
        }

        public static LezhuangResult success(String projectCode,
                                             List<Map<String, Object>> repairHistory,
                                             String note,
                                             String queriedAt) {
            List<Map<String, Object>> safeList = repairHistory == null
                ? Collections.<Map<String, Object>>emptyList()
                : repairHistory;
            return new LezhuangResult(
                safeList,
                buildInstallOrderDeepLink(projectCode),
                SourceStatus.of(SOURCE_NAME, true, note, queriedAt));
        }

        public static LezhuangResult degraded(String projectCode, String note, String queriedAt) {
            return new LezhuangResult(
                Collections.<Map<String, Object>>emptyList(),
                buildInstallOrderDeepLink(projectCode),
                SourceStatus.of(SOURCE_NAME, false, note, queriedAt));
        }

        private static String buildInstallOrderDeepLink(String projectCode) {
            return "https://lezhuang.internal/install/" + projectCode;
        }

        public List<Map<String, Object>> getRepairHistory() {
            return repairHistory;
        }

        public String getInstallOrderDeepLink() {
            return installOrderDeepLink;
        }

        public Map<String, Object> getSourceStatus() {
            return sourceStatus;
        }
    }
}
