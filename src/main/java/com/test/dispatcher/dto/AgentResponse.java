package com.test.dispatcher.dto;

public record AgentResponse(Object output, boolean success, String error) {

    public static AgentResponse ok(Object output) {
        return new AgentResponse(output, true, null);
    }

    public static AgentResponse error(String error) {
        return new AgentResponse(null, false, error);
    }
}
