package com.csg.workbench.problem.dto;

/**
 * 工单列表行 DTO（对应 /api/problems/board 列表条目，固定 camelCase 字段契约）。
 */
public class ProblemBoardRow {
    private String id;
    private Long displayNumber;
    private String ticketNo;
    private String title;
    private String device;
    private String state;
    private String owner;
    private String category;
    private String kind;
    private String channel;
    private Boolean devicePending;
    private String urgency;
    private String suspendReason;
    private String grade;
    private String disposalType;
    private Boolean compromiseFlag;
    private Boolean oldPartRegistered;
    private String escalationLevel;
    private String slaDueAt;
    private Integer version;
    private String updatedAt;
    private Integer activeFieldOrders;
    private String nextStep;
    private String nextDueAt;

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

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
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

    public Boolean getOldPartRegistered() {
        return oldPartRegistered;
    }

    public void setOldPartRegistered(Boolean oldPartRegistered) {
        this.oldPartRegistered = oldPartRegistered;
    }

    public String getEscalationLevel() {
        return escalationLevel;
    }

    public void setEscalationLevel(String escalationLevel) {
        this.escalationLevel = escalationLevel;
    }

    public String getSlaDueAt() {
        return slaDueAt;
    }

    public void setSlaDueAt(String slaDueAt) {
        this.slaDueAt = slaDueAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getActiveFieldOrders() {
        return activeFieldOrders;
    }

    public void setActiveFieldOrders(Integer activeFieldOrders) {
        this.activeFieldOrders = activeFieldOrders;
    }

    public String getNextStep() {
        return nextStep;
    }

    public void setNextStep(String nextStep) {
        this.nextStep = nextStep;
    }

    public String getNextDueAt() {
        return nextDueAt;
    }

    public void setNextDueAt(String nextDueAt) {
        this.nextDueAt = nextDueAt;
    }
}
