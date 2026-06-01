package com.test.dispatcher.dto;

import jakarta.validation.constraints.NotBlank;

public record AgentRequest (
        @NotBlank(message = "input must not be blank")
        String input
){}
