package com.csg.workbench.integration;

import java.util.Map;

/**
 * 售后安装系统集成客户端接口。
 */
public interface InstallClient {

    String SOURCE_NAME = "售后安装系统";

    /**
     * 查询设备安装投运信息。
     */
    InstallResult queryInstall(String sn, Map<String, Object> localRow, boolean simulateFailure, String queriedAt);

    final class InstallResult {
        private final Object installedAt;
        private final Map<String, Object> sourceStatus;

        private InstallResult(Object installedAt, Map<String, Object> sourceStatus) {
            this.installedAt = installedAt;
            this.sourceStatus = sourceStatus;
        }

        public static InstallResult success(Object installedAt, String note, String queriedAt) {
            return new InstallResult(
                installedAt,
                SourceStatus.of(SOURCE_NAME, true, note, queriedAt));
        }

        public static InstallResult degraded(String note, String queriedAt) {
            return new InstallResult(
                null,
                SourceStatus.of(SOURCE_NAME, false, note, queriedAt));
        }

        public Object getInstalledAt() {
            return installedAt;
        }

        public Map<String, Object> getSourceStatus() {
            return sourceStatus;
        }
    }
}
