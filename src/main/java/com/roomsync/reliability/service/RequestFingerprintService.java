package com.roomsync.reliability.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeMap;

/**
 * Service for generating deterministic, canonical SHA-256 request fingerprints.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RequestFingerprintService {

    private final ObjectMapper objectMapper;

    public String generateFingerprint(String httpMethod, String path, Long userId, Object requestPayload) {
        try {
            StringBuilder canonical = new StringBuilder();
            canonical.append(httpMethod != null ? httpMethod.toUpperCase() : "POST").append(":");
            canonical.append(normalizePath(path)).append(":");
            canonical.append(userId != null ? userId : "ANONYMOUS").append(":");

            if (requestPayload != null) {
                String normalizedJson = canonicalizePayload(requestPayload);
                canonical.append(normalizedJson);
            } else {
                canonical.append("{}");
            }

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 message digest algorithm not available", ex);
        }
    }

    private String canonicalizePayload(Object payload) {
        try {
            if (payload instanceof String strPayload) {
                if (strPayload.trim().isEmpty()) {
                    return "{}";
                }
                JsonNode tree = objectMapper.readTree(strPayload);
                return sortJsonNode(tree);
            }
            JsonNode tree = objectMapper.valueToTree(payload);
            return sortJsonNode(tree);
        } catch (Exception ex) {
            log.warn("Failed to parse JSON payload for canonical hashing; falling back to toString()", ex);
            return payload.toString();
        }
    }

    private String sortJsonNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isObject()) {
            TreeMap<String, Object> sortedMap = new TreeMap<>();
            node.fields().forEachRemaining(entry -> sortedMap.put(entry.getKey(), sortJsonNode(entry.getValue())));
            try {
                return objectMapper.writeValueAsString(sortedMap);
            } catch (Exception ex) {
                return node.toString();
            }
        }
        if (node.isArray()) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(sortJsonNode(node.get(i)));
            }
            sb.append("]");
            return sb.toString();
        }
        return node.asText();
    }

    private String normalizePath(String path) {
        if (path == null) return "";
        return path.replaceAll("/+$", "").toLowerCase();
    }
}
