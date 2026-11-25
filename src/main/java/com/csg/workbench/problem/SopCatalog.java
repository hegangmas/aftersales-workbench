package com.csg.workbench.problem;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SopCatalog {
    private SopCatalog() {
    }

    public static Map<String, Object> forCategory(String category) {
        String key = category == null ? "" : category.trim();
        if (key.contains("离线") || key.contains("通信") || key.contains("联网")) {
            return build(
                "离线与通信异常 SOP",
                "时序/状态类（照片无效，需故障时点与通信记录）",
                Arrays.asList(
                    "确认设备指示灯颜色与闪烁状态",
                    "请客户记录断网大致时间并观察周边信号",
                    "引导客户断电 1 分钟后重新上电观察重连"
                ),
                Arrays.asList(
                    "现场核查 SIM 卡槽与天线连接",
                    "记录程序版本与同站其他桩联网对照",
                    "采集本地通信日志并核对 SIM 到期时间"
                ),
                Arrays.asList("timestamp_log", "site_compare", "platform_data")
            );
        }
        if (key.contains("外观") || key.contains("枪") || key.contains("屏幕") || key.contains("破损")) {
            return build(
                "硬件外观与枪头异常 SOP",
                "可视类（以清晰照片或短视频为主）",
                Arrays.asList(
                    "确认现场无漏电或冒烟等安全风险",
                    "请客户拍摄铭牌编号与受损部位清晰照片",
                    "检查枪头锁止插针是否弯曲或有异物"
                ),
                Arrays.asList(
                    "现场核验枪线阻值与绝缘状态",
                    "留存拆检照片与旧件编号登记",
                    "换件后实车试充验证并留痕"
                ),
                Arrays.asList("photo", "video")
            );
        }
        return build(
            "充电故障标准排查 SOP",
            "时序 + 现象混合类（优先故障时点、告警报文与对照测试）",
            Arrays.asList(
                "确认故障发生精确时间（到分钟）及车辆插枪提示",
                "请客户观察桩体故障灯/屏幕报错并重新插拔枪头",
                "条件允许时引导换车或断电重启复测一次"
            ),
            Arrays.asList(
                "核对平台实时告警报文与历史充电中断记录",
                "现场测量 PE 接地、输入电压及同站对照测试",
                "执行单变量替换测试并留存复测依据"
            ),
            Arrays.asList("timestamp_log", "platform_data", "photo", "swap_test")
        );
    }

    private static Map<String, Object> build(String title, String evidenceMode, List<String> customerSteps,
                                             List<String> onsiteSteps, List<String> recommendedTypes) {
        Map<String, Object> sop = new LinkedHashMap<String, Object>();
        sop.put("title", title);
        sop.put("evidenceMode", evidenceMode);
        sop.put("customerSteps", customerSteps);
        sop.put("onsiteSteps", onsiteSteps);
        sop.put("recommendedEvidenceTypes", recommendedTypes);
        return sop;
    }
}
