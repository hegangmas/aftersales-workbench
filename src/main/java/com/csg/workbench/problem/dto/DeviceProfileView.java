package com.csg.workbench.problem.dto;

import java.util.List;
import java.util.Map;

/**
 * 设备 4 源聚合档案视图 DTO（对应 /api/devices/lookup 与 /api/devices/search 响应，固定 camelCase 字段契约）。
 */
public class DeviceProfileView {
    private String device;
    private String sn;
    private Integer matchCount;
    private String source;
    private String observedAt;
    private String fault;
    private String customerId;
    private String customerName;
    private String contactName;
    private String customerPhone;
    private String siteAddress;
    private String projectCode;
    private String projectName;
    private String customerType;
    private Integer slaMinutes;
    private String model;
    private String productType;
    private String manufacturedAt;
    private String mesBatch;
    private String mesTestResult;
    private String installedAt;
    private String warrantyStatus;
    private String warrantyUntil;
    private String extendedWarranty;
    private String simExpiresAt;
    private String firmwareVersion;
    private String onlineStatus;
    private String currentStatus;
    private String realtimeTelemetry;
    private List<Map<String, Object>> sourceStatuses;
    private List<Map<String, Object>> repairHistory;
    private List<Map<String, Object>> chargingRecords;
    private List<Map<String, Object>> recentAlerts;
    private List<Map<String, Object>> callRecordings;
    private Map<String, Object> simulatedCall;
    private List<Map<String, Object>> relatedProblems;
    private Map<String, String> deepLinks;

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public String getSn() {
        return sn;
    }

    public void setSn(String sn) {
        this.sn = sn;
    }

    public Integer getMatchCount() {
        return matchCount;
    }

    public void setMatchCount(Integer matchCount) {
        this.matchCount = matchCount;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(String observedAt) {
        this.observedAt = observedAt;
    }

    public String getFault() {
        return fault;
    }

    public void setFault(String fault) {
        this.fault = fault;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getSiteAddress() {
        return siteAddress;
    }

    public void setSiteAddress(String siteAddress) {
        this.siteAddress = siteAddress;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getCustomerType() {
        return customerType;
    }

    public void setCustomerType(String customerType) {
        this.customerType = customerType;
    }

    public Integer getSlaMinutes() {
        return slaMinutes;
    }

    public void setSlaMinutes(Integer slaMinutes) {
        this.slaMinutes = slaMinutes;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public String getManufacturedAt() {
        return manufacturedAt;
    }

    public void setManufacturedAt(String manufacturedAt) {
        this.manufacturedAt = manufacturedAt;
    }

    public String getMesBatch() {
        return mesBatch;
    }

    public void setMesBatch(String mesBatch) {
        this.mesBatch = mesBatch;
    }

    public String getMesTestResult() {
        return mesTestResult;
    }

    public void setMesTestResult(String mesTestResult) {
        this.mesTestResult = mesTestResult;
    }

    public String getInstalledAt() {
        return installedAt;
    }

    public void setInstalledAt(String installedAt) {
        this.installedAt = installedAt;
    }

    public String getWarrantyStatus() {
        return warrantyStatus;
    }

    public void setWarrantyStatus(String warrantyStatus) {
        this.warrantyStatus = warrantyStatus;
    }

    public String getWarrantyUntil() {
        return warrantyUntil;
    }

    public void setWarrantyUntil(String warrantyUntil) {
        this.warrantyUntil = warrantyUntil;
    }

    public String getExtendedWarranty() {
        return extendedWarranty;
    }

    public void setExtendedWarranty(String extendedWarranty) {
        this.extendedWarranty = extendedWarranty;
    }

    public String getSimExpiresAt() {
        return simExpiresAt;
    }

    public void setSimExpiresAt(String simExpiresAt) {
        this.simExpiresAt = simExpiresAt;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public void setFirmwareVersion(String firmwareVersion) {
        this.firmwareVersion = firmwareVersion;
    }

    public String getOnlineStatus() {
        return onlineStatus;
    }

    public void setOnlineStatus(String onlineStatus) {
        this.onlineStatus = onlineStatus;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public void setCurrentStatus(String currentStatus) {
        this.currentStatus = currentStatus;
    }

    public String getRealtimeTelemetry() {
        return realtimeTelemetry;
    }

    public void setRealtimeTelemetry(String realtimeTelemetry) {
        this.realtimeTelemetry = realtimeTelemetry;
    }

    public List<Map<String, Object>> getSourceStatuses() {
        return sourceStatuses;
    }

    public void setSourceStatuses(List<Map<String, Object>> sourceStatuses) {
        this.sourceStatuses = sourceStatuses;
    }

    public List<Map<String, Object>> getRepairHistory() {
        return repairHistory;
    }

    public void setRepairHistory(List<Map<String, Object>> repairHistory) {
        this.repairHistory = repairHistory;
    }

    public List<Map<String, Object>> getChargingRecords() {
        return chargingRecords;
    }

    public void setChargingRecords(List<Map<String, Object>> chargingRecords) {
        this.chargingRecords = chargingRecords;
    }

    public List<Map<String, Object>> getRecentAlerts() {
        return recentAlerts;
    }

    public void setRecentAlerts(List<Map<String, Object>> recentAlerts) {
        this.recentAlerts = recentAlerts;
    }

    public List<Map<String, Object>> getCallRecordings() {
        return callRecordings;
    }

    public void setCallRecordings(List<Map<String, Object>> callRecordings) {
        this.callRecordings = callRecordings;
    }

    public Map<String, Object> getSimulatedCall() {
        return simulatedCall;
    }

    public void setSimulatedCall(Map<String, Object> simulatedCall) {
        this.simulatedCall = simulatedCall;
    }

    public List<Map<String, Object>> getRelatedProblems() {
        return relatedProblems;
    }

    public void setRelatedProblems(List<Map<String, Object>> relatedProblems) {
        this.relatedProblems = relatedProblems;
    }

    public Map<String, String> getDeepLinks() {
        return deepLinks;
    }

    public void setDeepLinks(Map<String, String> deepLinks) {
        this.deepLinks = deepLinks;
    }
}
