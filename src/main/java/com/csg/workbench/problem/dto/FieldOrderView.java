package com.csg.workbench.problem.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 现场派单视图 DTO（对应 fieldOrders 明细项，固定 camelCase 字段契约）。
 */
public class FieldOrderView {
    private Long id;
    private String externalNo;
    private String state;
    private String receipt;
    @JsonProperty("isClaim")
    private Boolean isClaim;
    private Boolean returnToFactory;
    private String projectCode;
    private String appointmentAt;
    private String photoNames;
    private String acceptedAt;
    private String arrivedAt;
    private String completedAt;
    private String replacedParts;
    private String oldPartNo;
    private String oldPartPhoto;
    private String customerAcceptResult;
    private String feeOwner;
    private String rollbackAnomalyNote;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getExternalNo() {
        return externalNo;
    }

    public void setExternalNo(String externalNo) {
        this.externalNo = externalNo;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getReceipt() {
        return receipt;
    }

    public void setReceipt(String receipt) {
        this.receipt = receipt;
    }

    @JsonProperty("isClaim")
    public Boolean getIsClaim() {
        return isClaim;
    }

    @JsonProperty("isClaim")
    public void setIsClaim(Boolean isClaim) {
        this.isClaim = isClaim;
    }

    public Boolean getReturnToFactory() {
        return returnToFactory;
    }

    public void setReturnToFactory(Boolean returnToFactory) {
        this.returnToFactory = returnToFactory;
    }

    public String getProjectCode() {
        return projectCode;
    }

    public void setProjectCode(String projectCode) {
        this.projectCode = projectCode;
    }

    public String getAppointmentAt() {
        return appointmentAt;
    }

    public void setAppointmentAt(String appointmentAt) {
        this.appointmentAt = appointmentAt;
    }

    public String getPhotoNames() {
        return photoNames;
    }

    public void setPhotoNames(String photoNames) {
        this.photoNames = photoNames;
    }

    public String getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(String acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public String getArrivedAt() {
        return arrivedAt;
    }

    public void setArrivedAt(String arrivedAt) {
        this.arrivedAt = arrivedAt;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(String completedAt) {
        this.completedAt = completedAt;
    }

    public String getReplacedParts() {
        return replacedParts;
    }

    public void setReplacedParts(String replacedParts) {
        this.replacedParts = replacedParts;
    }

    public String getOldPartNo() {
        return oldPartNo;
    }

    public void setOldPartNo(String oldPartNo) {
        this.oldPartNo = oldPartNo;
    }

    public String getOldPartPhoto() {
        return oldPartPhoto;
    }

    public void setOldPartPhoto(String oldPartPhoto) {
        this.oldPartPhoto = oldPartPhoto;
    }

    public String getCustomerAcceptResult() {
        return customerAcceptResult;
    }

    public void setCustomerAcceptResult(String customerAcceptResult) {
        this.customerAcceptResult = customerAcceptResult;
    }

    public String getFeeOwner() {
        return feeOwner;
    }

    public void setFeeOwner(String feeOwner) {
        this.feeOwner = feeOwner;
    }

    public String getRollbackAnomalyNote() {
        return rollbackAnomalyNote;
    }

    public void setRollbackAnomalyNote(String rollbackAnomalyNote) {
        this.rollbackAnomalyNote = rollbackAnomalyNote;
    }
}
