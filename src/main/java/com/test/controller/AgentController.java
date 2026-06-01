package com.test.controller;

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

    public AgentController(AgentDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @PostMapping("/invoke")
    public ResponseEntity<AgentResponse> invoke(
            @Valid @RequestBody AgentRequest request) {
        try {
            String result = dispatcher.handle(request.input());
            return ResponseEntity.ok(AgentResponse.ok(result));
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
}
