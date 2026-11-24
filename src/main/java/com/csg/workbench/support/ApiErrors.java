package com.csg.workbench.support;

import java.util.regex.Pattern;

public final class ApiErrors {
    private static final Pattern SAFE = Pattern.compile(
        "^[\\u4e00-\\u9fffA-Za-z0-9，。、：；！？（）()《》\\-·\\s]{1,200}$");
    private static final Pattern UNSAFE = Pattern.compile(
        "exception|sql|stack|jdbc|password|secret|select |insert |update |delete ",
        Pattern.CASE_INSENSITIVE);

    private ApiErrors() {
    }

    public static String safeMessage(String candidate, int status) {
        if (candidate != null) {
            String text = candidate.trim();
            if (SAFE.matcher(text).matches() && !UNSAFE.matcher(text).find()) {
                return text;
            }
        }
        return fallback(status);
    }

    public static String fallback(int status) {
        switch (status) {
            case 400:
                return "请求无效";
            case 401:
                return "请重新登录";
            case 403:
                return "没有权限执行此操作";
            case 404:
                return "未找到对应记录";
            case 409:
                return "当前状态不允许此操作";
            case 503:
                return "服务暂不可用";
            default:
                return "暂时无法完成操作";
        }
    }
}
