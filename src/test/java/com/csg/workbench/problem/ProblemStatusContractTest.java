package com.csg.workbench.problem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

/**
 * 状态单一源契约测试：校验 web/src/contracts/problem-status.json、ProblemStatus 与 ProblemStateMachine 完全一致。
 */
class ProblemStatusContractTest {
    private static final List<String> LEGACY_CODES = Arrays.asList(
        "open", "waiting", "field", "verify", "resolved", "triage", "answered", "referred"
    );

    private static JsonNode loadContract() throws IOException {
        Path path = Paths.get("web", "src", "contracts", "problem-status.json");
        if (!Files.exists(path)) {
            Path cursor = Paths.get("").toAbsolutePath();
            while (cursor != null) {
                Path candidate = cursor.resolve("web").resolve("src").resolve("contracts").resolve("problem-status.json");
                if (Files.exists(candidate)) {
                    path = candidate;
                    break;
                }
                cursor = cursor.getParent();
            }
        }
        assertTrue(Files.exists(path), "Contract file not found: " + path.toAbsolutePath());
        return new ObjectMapper().readTree(path.toFile());
    }

    private static boolean stateMachineAllowsCrossTransition(String from, String to) {
        if (from.equals(to)) {
            return false;
        }
        try {
            ProblemStateMachine.requireTransition(from, to);
            return true;
        } catch (ResponseStatusException ex) {
            return false;
        }
    }

    @Test
    void v11StatesMatchProblemStatusEnumAndStateMachine() throws IOException {
        JsonNode root = loadContract();
        JsonNode v11States = root.get("v11States");
        assertNotNull(v11States, "v11States must exist in problem-status.json");
        assertTrue(v11States.isArray());

        Set<String> jsonCodes = new HashSet<String>();
        for (JsonNode stateNode : v11States) {
            String code = stateNode.get("code").asText();
            String label = stateNode.get("label").asText();
            boolean terminal = stateNode.get("terminal").asBoolean();

            assertTrue(jsonCodes.add(code), "Duplicate code in v11States: " + code);
            ProblemStatus status = ProblemStatus.fromCode(code);
            assertEquals(code, status.code());
            assertEquals(label, status.label());
            assertEquals(terminal, status.isTerminal());
            assertEquals(label, ProblemStateMachine.label(code));
            assertEquals(terminal, ProblemStateMachine.isTerminal(code));
        }

        Set<String> enumCodes = new HashSet<String>();
        for (ProblemStatus status : ProblemStatus.values()) {
            enumCodes.add(status.code());
        }

        assertEquals(enumCodes, jsonCodes, "JSON v11States and ProblemStatus.values() must match 1-to-1");
        assertEquals(ProblemStateMachine.ALL_STATES, jsonCodes, "JSON v11States and ProblemStateMachine.ALL_STATES must match 1-to-1");
    }

    @Test
    void allowedTransitionsMatchStateMachineAcrossAllSevenBySevenPairs() throws IOException {
        JsonNode root = loadContract();
        JsonNode v11States = root.get("v11States");

        Map<String, Set<String>> jsonTransitions = new HashMap<String, Set<String>>();
        for (JsonNode stateNode : v11States) {
            String from = stateNode.get("code").asText();
            Set<String> targets = new HashSet<String>();
            JsonNode allowedNode = stateNode.get("allowedTransitions");
            assertNotNull(allowedNode, "allowedTransitions missing for " + from);
            for (JsonNode targetNode : allowedNode) {
                String target = targetNode.asText();
                assertTrue(ProblemStatus.isValidCode(target), "Unknown transition target in JSON: " + target);
                assertFalse(from.equals(target), "allowedTransitions should not list self-transition for " + from);
                targets.add(target);
            }
            jsonTransitions.put(from, targets);
        }

        for (ProblemStatus fromStatus : ProblemStatus.values()) {
            String from = fromStatus.code();
            Set<String> allowedFromJson = jsonTransitions.get(from);
            assertNotNull(allowedFromJson, "Missing transitions entry for " + from);
            if (fromStatus.isTerminal()) {
                assertTrue(allowedFromJson.isEmpty(), "Terminal state must have empty allowedTransitions: " + from);
            }
            for (ProblemStatus toStatus : ProblemStatus.values()) {
                String to = toStatus.code();
                if (from.equals(to)) {
                    ProblemStateMachine.requireTransition(from, to);
                    assertFalse(allowedFromJson.contains(to));
                } else {
                    boolean expected = allowedFromJson.contains(to);
                    boolean actual = stateMachineAllowsCrossTransition(from, to);
                    assertEquals(expected, actual, "Transition mismatch for " + from + " -> " + to);
                }
            }
        }
    }

    @Test
    void legacyCodesAreRejectedByV11ValidatorsAndMarkedDisplayOnlyInContract() throws IOException {
        JsonNode root = loadContract();
        JsonNode legacyMap = root.get("legacyDisplayMap");
        assertNotNull(legacyMap, "legacyDisplayMap must exist in problem-status.json");

        for (String legacyCode : LEGACY_CODES) {
            assertFalse(ProblemStatus.isValidCode(legacyCode), "Legacy code must not be valid V1.1 status: " + legacyCode);
            assertThrows(IllegalArgumentException.class, () -> ProblemStatus.fromCode(legacyCode));
            assertFalse(ProblemStateMachine.ALL_STATES.contains(legacyCode));
            assertThrows(ResponseStatusException.class,
                () -> ProblemStateMachine.requireTransition(legacyCode, ProblemStateMachine.PROCESSING));
            assertThrows(ResponseStatusException.class,
                () -> ProblemStateMachine.requireTransition(ProblemStateMachine.PROCESSING, legacyCode));

            JsonNode entry = legacyMap.get(legacyCode);
            assertNotNull(entry, "legacyDisplayMap missing legacy code: " + legacyCode);
            assertTrue(entry.get("displayOnly").asBoolean(), "Legacy mapping must be displayOnly: " + legacyCode);
            String semantic = entry.get("v11Semantic").asText();
            assertNotNull(ProblemStatus.fromCode(semantic), "Legacy mapping v11Semantic must be valid V1.1 code: " + semantic);
        }

        Iterator<String> fieldNames = legacyMap.fieldNames();
        while (fieldNames.hasNext()) {
            String code = fieldNames.next();
            assertFalse(ProblemStatus.isValidCode(code), "legacyDisplayMap must not contain V1.1 code: " + code);
        }
    }
}
