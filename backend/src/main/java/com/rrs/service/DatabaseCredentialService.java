package com.rrs.service;

import com.rrs.entity.Credential;
import com.rrs.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseCredentialService {

    private final CredentialService credentialService;

    /**
     * Test database connectivity using saved credential.
     */
    public String testConnection(Long credentialId) {
        log.info("Testing DB connection for credential: {}", credentialId);
        Credential credential = credentialService.getEntity(credentialId);
        Map<String, Object> params = credentialService.getParams(credential);
        return testConnectionDirect(params);
    }

    /**
     * Test database connectivity using raw params.
     */
    public String testConnectionDirect(Map<String, Object> params) {
        String jdbcUrl = getRequired(params, "jdbcUrl");
        String driverClass = getRequired(params, "driverClass");
        String username = getOrDefault(params, "username", "");
        String password = getOrDefault(params, "password", "");

        log.info("Testing DB connection: url={}, driver={}", jdbcUrl, driverClass);

        // Load driver class
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {
            return "FAILED: Driver class not found - " + driverClass;
        }

        Properties props = new Properties();
        if (!username.isBlank()) {
            props.setProperty("user", username);
        }
        if (!password.isBlank()) {
            props.setProperty("password", password);
        }
        props.setProperty("loginTimeout", "10");

        try (Connection conn = DriverManager.getConnection(jdbcUrl, props)) {
            String productName = conn.getMetaData().getDatabaseProductName();
            String version = conn.getMetaData().getDatabaseProductVersion();
            return "Connection successful. Database: " + productName + " " + version;
        } catch (SQLException e) {
            log.error("DB connection test failed", e);
            return "FAILED: " + e.getMessage();
        }
    }

    private String getRequired(Map<String, Object> params, String key) {
        Object val = params.get(key);
        if (val == null || val.toString().isBlank()) {
            throw new BusinessException(400, "Missing required param: " + key);
        }
        return val.toString().trim();
    }

    private String getOrDefault(Map<String, Object> params, String key, String defaultVal) {
        Object val = params.get(key);
        if (val == null || val.toString().isBlank()) return defaultVal;
        return val.toString().trim();
    }
}
