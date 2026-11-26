package com.csg.workbench.problem.dto;

import java.util.List;
import java.util.Map;

/**
 * 工单详情聚合视图 DTO（对应 /api/problems/{id} 响应，固定 camelCase 字段契约）。
 */
public class ProblemDetailView {
    private String id;
    private Long displayNumber;
    private String ticketNo;
    private String title;
    private String device;
    private String state;
    private String description;
    private String resumeState;
    private String category;
    private String kind;
    private String channel;
    private String owner;
    private Integer version;
    private String faultOccurredAt;
    private String suspendReason;
    private String grade;
    private String disposalType;
    private Boolean compromiseFlag;
    private String escalationLevel;
    private String escalationReason;
    private String slaDueAt;
    private String autoApprovalNote;
    private String customerId;
    private Boolean devicePending;
    private String urgency;
    private String symptomTags;
    private String acceptedBy;
    private String warrantyConfirmed;
    private String snapshotJson;
    private Boolean oldPartRegistered;
    private String oldPartNo;
    private String reopenedFromId;
    private String createdAt;
    private String updatedAt;

    private Map<String, Object> pendingFollowup;
    private List<FieldOrderView> fieldOrders;
    private List<Map<String, Object>> callbackResults;
    private List<Map<String, Object>> activities;
    private List<Map<String, Object>> ownershipEvents;
    private List<Map<String, Object>> evidences;
    private List<Map<String, Object>> transitions;
    private List<Map<String, Object>> assessments;
    private Map<String, Object> sop;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getDisplayNumber() {
        return displayNumber;
    }

    public void setDisplayNumber(Long displayNumber) {
        this.displayNumber = displayNumber;
    }

    public String getTicketNo() {
        return ticketNo;
    }

    public void setTicketNo(String ticketNo) {
        this.ticketNo = ticketNo;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getResumeState() {
        return resumeState;
    }

    public void setResumeState(String resumeState) {
        this.resumeState = resumeState;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getFaultOccurredAt() {
        return faultOccurredAt;
    }

    public void setFaultOccurredAt(String faultOccurredAt) {
        this.faultOccurredAt = faultOccurredAt;
    }

    public String getSuspendReason() {
        return suspendReason;
    }

    public void setSuspendReason(String suspendReason) {
        this.suspendReason = suspendReason;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getDisposalType() {
        return disposalType;
    }

    public void setDisposalType(String disposalType) {
        this.disposalType = disposalType;
    }

    public Boolean getCompromiseFlag() {
        return compromiseFlag;
    }

    public void setCompromiseFlag(Boolean compromiseFlag) {
        this.compromiseFlag = compromiseFlag;
    }

    public String getEscalationLevel() {
        return escalationLevel;
    }

    public void setEscalationLevel(String escalationLevel) {
        this.escalationLevel = escalationLevel;
    }

    public String getEscalationReason() {
        return escalationReason;
    }

    public void setEscalationReason(String escalationReason) {
        this.escalationReason = escalationReason;
    }

    public String getSlaDueAt() {
        return slaDueAt;
    }

    public void setSlaDueAt(String slaDueAt) {
        this.slaDueAt = slaDueAt;
    }

    public String getAutoApprovalNote() {
        return autoApprovalNote;
    }

    public void setAutoApprovalNote(String autoApprovalNote) {
        this.autoApprovalNote = autoApprovalNote;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public Boolean getDevicePending() {
        return devicePending;
    }

    public void setDevicePending(Boolean devicePending) {
        this.devicePending = devicePending;
    }

    public String getUrgency() {
        return urgency;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    public String getSymptomTags() {
        return symptomTags;
    }

    public void setSymptomTags(String symptomTags) {
        this.symptomTags = symptomTags;
    }

    public String getAcceptedBy() {
        return acceptedBy;
    }

    public void setAcceptedBy(String acceptedBy) {
        this.acceptedBy = acceptedBy;
    }

    public String getWarrantyConfirmed() {
        return warrantyConfirmed;
    }

    public void setWarrantyConfirmed(String warrantyConfirmed) {
        this.warrantyConfirmed = warrantyConfirmed;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public Boolean getOldPartRegistered() {
        return oldPartRegistered;
    }

    public void setOldPartRegistered(Boolean oldPartRegistered) {
        this.oldPartRegistered = oldPartRegistered;
    }

    public String getOldPartNo() {
        return oldPartNo;
    }

    public void setOldPartNo(String oldPartNo) {
        this.oldPartNo = oldPartNo;
    }

    public String getReopenedFromId() {
        return reopenedFromId;
    }

    public void setReopenedFromId(String reopenedFromId) {
        this.reopenedFromId = reopenedFromId;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Map<String, Object> getPendingFollowup() {
        return pendingFollowup;
    }

    public void setPendingFollowup(Map<String, Object> pendingFollowup) {
        this.pendingFollowup = pendingFollowup;
    }

    public List<FieldOrderView> getFieldOrders() {
        return fieldOrders;
    }

    public void setFieldOrders(List<FieldOrderView> fieldOrders) {
        this.fieldOrders = fieldOrders;
    }

    public List<Map<String, Object>> getCallbackResults() {
        return callbackResults;
    }

    public void setCallbackResults(List<Map<String, Object>> callbackResults) {
        this.callbackResults = callbackResults;
    }

    public List<Map<String, Object>> getActivities() {
        return activities;
    }

    public void setActivities(List<Map<String, Object>> activities) {
        this.activities = activities;
    }

    public List<Map<String, Object>> getOwnershipEvents() {
        return ownershipEvents;
    }

    public void setOwnershipEvents(List<Map<String, Object>> ownershipEvents) {
        this.ownershipEvents = ownershipEvents;
    }

    public List<Map<String, Object>> getEvidences() {
        return evidences;
    }

    public void setEvidences(List<Map<String, Object>> evidences) {
        this.evidences = evidences;
    }

    public List<Map<String, Object>> getTransitions() {
        return transitions;
    }

    public void setTransitions(List<Map<String, Object>> transitions) {
        this.transitions = transitions;
    }

    public List<Map<String, Object>> getAssessments() {
        return assessments;
    }

    public void setAssessments(List<Map<String, Object>> assessments) {
        this.assessments = assessments;
    }

    public Map<String, Object> getSop() {
        return sop;
    }

    public void setSop(Map<String, Object> sop) {
        this.sop = sop;
    }
}
