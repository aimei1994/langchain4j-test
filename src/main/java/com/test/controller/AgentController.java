package com.test.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.test.dispatcher.AgentDispatcher;
import com.test.dispatcher.dto.AgentRequest;
import com.test.dispatcher.dto.AgentResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/agent")
public class AgentController {

    private static final Logger log = LoggerFactory.getLogger(AgentController.class);
    private final AgentDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    public AgentController(AgentDispatcher dispatcher, ObjectMapper objectMapper) {
        this.dispatcher   = dispatcher;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/invoke")
    public ResponseEntity<AgentResponse> invoke(
            @Valid @RequestBody AgentRequest request) {
        try {
            String result = dispatcher.handle(request.input());
            return ResponseEntity.ok(AgentResponse.ok(parseOrRaw(result)));
        } catch (Exception e) {
            log.error("Error invoking skill: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(AgentResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/skills")
    public ResponseEntity<AgentResponse> listSkills() {
        return ResponseEntity.ok(AgentResponse.ok(dispatcher.listAvailable()));
    }

    private Object parseOrRaw(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return text;
        try {
            JsonNode node = objectMapper.readValue(trimmed, JsonNode.class);
            stripLineNumberPrefixes(node);
            return node;
        } catch (Exception e) {
            return text;
        }
    }

    private void stripLineNumberPrefixes(JsonNode node) {
        if (node.isArray()) {
            node.forEach(this::stripFromObject);
        } else if (node.isObject()) {
            stripFromObject(node);
        }
    }

    private void stripFromObject(JsonNode node) {
        if (!node.isObject()) return;
        ObjectNode obj = (ObjectNode) node;
        if (obj.has("existingCode") && obj.has("suggestionCode")) {
            obj.put("existingCode",   stripPrefix(obj.get("existingCode").asText()));
            obj.put("suggestionCode", stripPrefix(obj.get("suggestionCode").asText()));
        }
    }

    private String stripPrefix(String code) {
        if (code == null) return null;
        return code.replaceAll("(?m)^\\d+: ", "");
    }
}
