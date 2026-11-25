package com.csg.workbench.device;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DeviceMapper {
    @Select("SELECT d.sn, d.customer_id AS customerId, d.model, d.product_type AS productType, "
        + "d.manufactured_at AS manufacturedAt, d.mes_batch AS mesBatch, d.mes_test_result AS mesTestResult, "
        + "d.installed_at AS installedAt, d.warranty_status AS warrantyStatus, d.warranty_until AS warrantyUntil, "
        + "d.extended_warranty AS extendedWarranty, d.sim_expires_at AS simExpiresAt, "
        + "d.firmware_version AS firmwareVersion, d.online_status AS onlineStatus, "
        + "d.current_status AS currentStatus, d.realtime_telemetry AS realtimeTelemetry, "
        + "d.replaced_from_sn AS replacedFromSn, "
        + "c.name AS customerName, c.contact_name AS contactName, c.phone AS customerPhone, "
        + "c.project_code AS projectCode, c.project_name AS projectName, c.address AS siteAddress, "
        + "c.customer_type AS customerType, c.sla_minutes AS slaMinutes "
        + "FROM device_profile d LEFT JOIN customer_profile c ON c.id = d.customer_id "
        + "WHERE UPPER(d.sn) = UPPER(#{query}) "
        + "OR c.phone = #{query} "
        + "OR UPPER(c.project_code) = UPPER(#{query}) "
        + "OR d.sn IN (SELECT m.sn FROM phone_sn_mapping m WHERE m.phone = #{query}) "
        + "ORDER BY d.updated_at DESC LIMIT 10")
    List<Map<String, Object>> searchDevices(@Param("query") String query);

    @Select("SELECT d.sn, d.customer_id AS customerId, d.model, d.product_type AS productType, "
        + "d.manufactured_at AS manufacturedAt, d.mes_batch AS mesBatch, d.mes_test_result AS mesTestResult, "
        + "d.installed_at AS installedAt, d.warranty_status AS warrantyStatus, d.warranty_until AS warrantyUntil, "
        + "d.extended_warranty AS extendedWarranty, d.sim_expires_at AS simExpiresAt, "
        + "d.firmware_version AS firmwareVersion, d.online_status AS onlineStatus, "
        + "d.current_status AS currentStatus, d.realtime_telemetry AS realtimeTelemetry, "
        + "c.name AS customerName, c.contact_name AS contactName, c.phone AS customerPhone, "
        + "c.project_code AS projectCode, c.project_name AS projectName, c.address AS siteAddress, "
        + "c.customer_type AS customerType, c.sla_minutes AS slaMinutes "
        + "FROM device_profile d LEFT JOIN customer_profile c ON c.id = d.customer_id "
        + "WHERE d.sn = #{sn}")
    Map<String, Object> findBySn(@Param("sn") String sn);

    @Select("SELECT id, name, contact_name AS contactName, phone, project_code AS projectCode, "
        + "project_name AS projectName, address, customer_type AS customerType, sla_minutes AS slaMinutes "
        + "FROM customer_profile ORDER BY id")
    List<Map<String, Object>> listCustomers();

    @Insert("INSERT INTO customer_profile (id, name, contact_name, phone, project_code, project_name, address, customer_type, sla_minutes) "
        + "VALUES (#{id}, #{name}, #{contactName}, #{phone}, #{projectCode}, #{projectName}, #{address}, #{customerType}, #{slaMinutes})")
    void insertCustomer(@Param("id") String id, @Param("name") String name, @Param("contactName") String contactName,
                        @Param("phone") String phone, @Param("projectCode") String projectCode,
                        @Param("projectName") String projectName, @Param("address") String address,
                        @Param("customerType") String customerType, @Param("slaMinutes") int slaMinutes);

    @Insert("INSERT INTO device_profile (sn, customer_id, model, product_type, manufactured_at, mes_batch, mes_test_result, "
        + "installed_at, warranty_status, warranty_until, extended_warranty, sim_expires_at, firmware_version) "
        + "VALUES (#{sn}, #{customerId}, #{model}, #{productType}, #{manufacturedAt}, #{mesBatch}, '出厂测试通过', "
        + "#{installedAt}, #{warrantyStatus}, #{warrantyUntil}, '无', #{simExpiresAt}, #{firmwareVersion})")
    void insertDevice(@Param("sn") String sn, @Param("customerId") String customerId, @Param("model") String model,
                      @Param("productType") String productType, @Param("manufacturedAt") String manufacturedAt,
                      @Param("mesBatch") String mesBatch, @Param("installedAt") String installedAt,
                      @Param("warrantyStatus") String warrantyStatus, @Param("warrantyUntil") String warrantyUntil,
                      @Param("simExpiresAt") String simExpiresAt, @Param("firmwareVersion") String firmwareVersion);
}
