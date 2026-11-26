package com.csg.workbench.problem.dto;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 热点响应视图组装器：将 Mapper / Service 返回的弱类型 Map 统一转为强类型 camelCase DTO。
 */
@Component
public class ProblemViewAssembler {

    public FieldOrderView toFieldOrderView(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        FieldOrderView view = new FieldOrderView();
        view.setId(asLong(row.get("id")));
        view.setExternalNo(asString(row.get("externalNo")));
        view.setState(asString(row.get("state")));
        view.setReceipt(asString(row.get("receipt")));
        view.setIsClaim(asBoolean(row.get("isClaim")));
        view.setReturnToFactory(asBoolean(row.get("returnToFactory")));
        view.setProjectCode(asString(row.get("projectCode")));
        view.setAppointmentAt(asTimestampText(row.get("appointmentAt")));
        view.setPhotoNames(asString(row.get("photoNames")));
        view.setAcceptedAt(asTimestampText(row.get("acceptedAt")));
        view.setArrivedAt(asTimestampText(row.get("arrivedAt")));
        view.setCompletedAt(asTimestampText(row.get("completedAt")));
        view.setReplacedParts(asString(row.get("replacedParts")));
        view.setOldPartNo(asString(row.get("oldPartNo")));
        view.setOldPartPhoto(asString(row.get("oldPartPhoto")));
        view.setCustomerAcceptResult(asString(row.get("customerAcceptResult")));
        view.setFeeOwner(asString(row.get("feeOwner")));
        view.setRollbackAnomalyNote(asString(row.get("rollbackAnomalyNote")));
        return view;
    }

    public List<FieldOrderView> toFieldOrderViews(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<FieldOrderView> result = new ArrayList<FieldOrderView>(rows.size());
        for (Map<String, Object> row : rows) {
            FieldOrderView view = toFieldOrderView(row);
            if (view != null) {
                result.add(view);
            }
        }
        return result;
    }

    public ProblemBoardRow toBoardRow(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        ProblemBoardRow view = new ProblemBoardRow();
        view.setId(asString(row.get("id")));
        view.setDisplayNumber(asLong(row.get("displayNumber")));
        view.setTicketNo(asString(row.get("ticketNo")));
        view.setTitle(asString(row.get("title")));
        view.setDevice(asString(row.get("device")));
        view.setState(asString(row.get("state")));
        view.setOwner(asString(row.get("owner")));
        view.setCategory(asString(row.get("category")));
        view.setKind(asString(row.get("kind")));
        view.setChannel(asString(row.get("channel")));
        view.setDevicePending(asBoolean(row.get("devicePending")));
        view.setUrgency(asString(row.get("urgency")));
        view.setSuspendReason(asString(row.get("suspendReason")));
        view.setGrade(asString(row.get("grade")));
        view.setDisposalType(asString(row.get("disposalType")));
        view.setCompromiseFlag(asBoolean(row.get("compromiseFlag")));
        view.setOldPartRegistered(asBoolean(row.get("oldPartRegistered")));
        view.setEscalationLevel(asString(row.get("escalationLevel")));
        view.setSlaDueAt(asTimestampText(row.get("slaDueAt")));
        view.setVersion(asInteger(row.get("version")));
        view.setUpdatedAt(asTimestampText(row.get("updatedAt")));
        view.setActiveFieldOrders(asInteger(row.get("activeFieldOrders")));
        view.setNextStep(asString(row.get("nextStep")));
        view.setNextDueAt(asTimestampText(row.get("nextDueAt")));
        return view;
    }

    public List<ProblemBoardRow> toBoardRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProblemBoardRow> result = new ArrayList<ProblemBoardRow>(rows.size());
        for (Map<String, Object> row : rows) {
            ProblemBoardRow view = toBoardRow(row);
            if (view != null) {
                result.add(view);
            }
        }
        return result;
    }

    public ProblemDetailView toDetailView(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        ProblemDetailView view = new ProblemDetailView();
        view.setId(asString(row.get("id")));
        view.setDisplayNumber(asLong(row.get("displayNumber")));
        view.setTicketNo(asString(row.get("ticketNo")));
        view.setTitle(asString(row.get("title")));
        view.setDevice(asString(row.get("device")));
        view.setState(asString(row.get("state")));
        view.setDescription(asString(row.get("description")));
        view.setResumeState(asString(row.get("resumeState")));
        view.setCategory(asString(row.get("category")));
        view.setKind(asString(row.get("kind")));
        view.setChannel(asString(row.get("channel")));
        view.setOwner(asString(row.get("owner")));
        view.setVersion(asInteger(row.get("version")));
        view.setFaultOccurredAt(asTimestampText(row.get("faultOccurredAt")));
        view.setSuspendReason(asString(row.get("suspendReason")));
        view.setGrade(asString(row.get("grade")));
        view.setDisposalType(asString(row.get("disposalType")));
        view.setCompromiseFlag(asBoolean(row.get("compromiseFlag")));
        view.setEscalationLevel(asString(row.get("escalationLevel")));
        view.setEscalationReason(asString(row.get("escalationReason")));
        view.setSlaDueAt(asTimestampText(row.get("slaDueAt")));
        view.setAutoApprovalNote(asString(row.get("autoApprovalNote")));
        view.setCustomerId(asString(row.get("customerId")));
        view.setDevicePending(asBoolean(row.get("devicePending")));
        view.setUrgency(asString(row.get("urgency")));
        view.setSymptomTags(asString(row.get("symptomTags")));
        view.setAcceptedBy(asString(row.get("acceptedBy")));
        view.setWarrantyConfirmed(asString(row.get("warrantyConfirmed")));
        view.setSnapshotJson(asString(row.get("snapshotJson")));
        view.setOldPartRegistered(asBoolean(row.get("oldPartRegistered")));
        view.setOldPartNo(asString(row.get("oldPartNo")));
        view.setReopenedFromId(asString(row.get("reopenedFromId")));
        view.setCreatedAt(asTimestampText(row.get("createdAt")));
        view.setUpdatedAt(asTimestampText(row.get("updatedAt")));

        view.setPendingFollowup(asMap(row.get("pendingFollowup")));
        view.setFieldOrders(toFieldOrderViews(asMapList(row.get("fieldOrders"))));
        view.setCallbackResults(asMapList(row.get("callbackResults")));
        view.setActivities(asMapList(row.get("activities")));
        view.setOwnershipEvents(asMapList(row.get("ownershipEvents")));
        view.setEvidences(asMapList(row.get("evidences")));
        view.setTransitions(asMapList(row.get("transitions")));
        view.setAssessments(asMapList(row.get("assessments")));
        view.setSop(asMap(row.get("sop")));
        return view;
    }

    public DeviceProfileView toDeviceProfileView(Map<String, Object> row) {
        if (row == null) {
            return null;
        }
        DeviceProfileView view = new DeviceProfileView();
        view.setDevice(asString(row.get("device")));
        view.setSn(asString(row.get("sn")));
        view.setMatchCount(asInteger(row.get("matchCount")));
        view.setSource(asString(row.get("source")));
        view.setObservedAt(asTimestampText(row.get("observedAt")));
        view.setFault(asString(row.get("fault")));
        view.setCustomerId(asString(row.get("customerId")));
        view.setCustomerName(asString(row.get("customerName")));
        view.setContactName(asString(row.get("contactName")));
        view.setCustomerPhone(asString(row.get("customerPhone")));
        view.setSiteAddress(asString(row.get("siteAddress")));
        view.setProjectCode(asString(row.get("projectCode")));
        view.setProjectName(asString(row.get("projectName")));
        view.setCustomerType(asString(row.get("customerType")));
        view.setSlaMinutes(asInteger(row.get("slaMinutes")));
        view.setModel(asString(row.get("model")));
        view.setProductType(asString(row.get("productType")));
        view.setManufacturedAt(asString(row.get("manufacturedAt")));
        view.setMesBatch(asString(row.get("mesBatch")));
        view.setMesTestResult(asString(row.get("mesTestResult")));
        view.setInstalledAt(asString(row.get("installedAt")));
        view.setWarrantyStatus(asString(row.get("warrantyStatus")));
        view.setWarrantyUntil(asString(row.get("warrantyUntil")));
        view.setExtendedWarranty(asString(row.get("extendedWarranty")));
        view.setSimExpiresAt(asString(row.get("simExpiresAt")));
        view.setFirmwareVersion(asString(row.get("firmwareVersion")));
        view.setOnlineStatus(asString(row.get("onlineStatus")));
        view.setCurrentStatus(asString(row.get("currentStatus")));
        view.setRealtimeTelemetry(asString(row.get("realtimeTelemetry")));
        view.setSourceStatuses(asMapList(row.get("sourceStatuses")));
        view.setRepairHistory(asMapList(row.get("repairHistory")));
        view.setChargingRecords(asMapList(row.get("chargingRecords")));
        view.setRecentAlerts(asMapList(row.get("recentAlerts")));
        view.setCallRecordings(asMapList(row.get("callRecordings")));
        view.setSimulatedCall(asMap(row.get("simulatedCall")));
        view.setRelatedProblems(asMapList(row.get("relatedProblems")));
        view.setDeepLinks(asStringMap(row.get("deepLinks")));
        return view;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Long.valueOf(((Number) value).longValue());
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Integer.valueOf(((Number) value).intValue());
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Boolean asBoolean(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return Boolean.valueOf(((Number) value).intValue() != 0);
        }
        String text = String.valueOf(value).trim();
        if ("1".equals(text) || "true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("0".equals(text) || "false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static String asTimestampText(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return value.toString();
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime().toString();
        }
        return String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asMapList(Object value) {
        if (!(value instanceof List)) {
            return Collections.emptyList();
        }
        List<?> raw = (List<?>) value;
        List<Map<String, Object>> out = new ArrayList<Map<String, Object>>(raw.size());
        for (Object item : raw) {
            if (item instanceof Map) {
                out.add((Map<String, Object>) item);
            }
        }
        return out;
    }

    private static Map<String, String> asStringMap(Object value) {
        if (!(value instanceof Map)) {
            return Collections.emptyMap();
        }
        Map<?, ?> raw = (Map<?, ?>) value;
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null) {
                out.put(String.valueOf(entry.getKey()), entry.getValue() == null ? null : String.valueOf(entry.getValue()));
            }
        }
        return out;
    }
}
