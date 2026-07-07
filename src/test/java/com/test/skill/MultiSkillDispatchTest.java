package com.test.skill;

import com.test.dispatcher.AgentDispatcher;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// Proves /skillA,skillB dispatches N separate calls — one per skill, each
// seeing only that skill's own content — rather than one call with every
// requested skill's body merged into a single system message.
class MultiSkillDispatchTest {

    private static final String SUMMARIZE_MARKER = "Summarize the given text into 3 bullet points";
    private static final String WEATHER_MARKER = "Given a city name, return weather info as JSON only";

    private SkillService skillService;
    private AgentDispatcher dispatcher;

    // Echoes back the system message it received, so tests can assert exactly
    // which skill's content reached the model on each call.
    private static class EchoingFakeChatModel implements ChatModel {
        @Override
        public ChatResponse chat(ChatRequest request) {
            String systemText = SystemMessage.findFirst(request.messages())
                    .map(SystemMessage::text)
                    .orElse("<no system message>");
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from("ECHO: " + systemText))
                    .build();
        }
    }

    @BeforeEach
    void setUp() {
        skillService = new SkillService(new EchoingFakeChatModel(), "skills", new FileSystemTools());
        skillService.init();
        dispatcher = new AgentDispatcher(skillService);
    }

    @Test
    void invokeWithMultipleSkillNamesRunsEachInItsOwnIsolatedCall() {
        Map<String, String> results = skillService.invoke(List.of("summarize", "weather"), "some input");

        assertThat(results).containsOnlyKeys("summarize", "weather");

        assertThat(results.get("summarize")).contains(SUMMARIZE_MARKER);
        assertThat(results.get("summarize")).doesNotContain(WEATHER_MARKER);

        assertThat(results.get("weather")).contains(WEATHER_MARKER);
        assertThat(results.get("weather")).doesNotContain(SUMMARIZE_MARKER);
    }

    @Test
    void dispatcherRoutesCommaSeparatedSkillsToInvokeAll() {
        Object result = dispatcher.handle("/summarize,weather some input");

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, String> results = (Map<String, String>) result;

        assertThat(results).containsOnlyKeys("summarize", "weather");
        assertThat(results.get("summarize")).contains(SUMMARIZE_MARKER);
        assertThat(results.get("weather")).contains(WEATHER_MARKER);
    }

    @Test
    void dispatcherStillReturnsRawStringForSingleSkill() {
        Object result = dispatcher.handle("/summarize some input");

        assertThat(result).isInstanceOf(String.class);
        assertThat((String) result).contains(SUMMARIZE_MARKER);
    }

    @Test
    void dispatcherParsesSkillMentionsFromFreeTextWithColonPayload() {
        Object result = dispatcher.handle(
                "make sure to use /summarize and /weather to review following code: the-actual-code-here();");

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, String> results = (Map<String, String>) result;

        assertThat(results).containsOnlyKeys("summarize", "weather");
        assertThat(results.get("summarize")).contains(SUMMARIZE_MARKER);
        assertThat(results.get("weather")).contains(WEATHER_MARKER);
    }

    @Test
    void dispatcherParsesSkillMentionsFromFreeTextWithFencedCodeBlock() {
        Object result = dispatcher.handle(
                "please use /summarize and /weather on this:\n```\nthe-actual-code-here();\n```");

        assertThat(result).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, String> results = (Map<String, String>) result;

        assertThat(results).containsOnlyKeys("summarize", "weather");
    }

    @Test
    void dispatcherIgnoresUnknownSlashMentionsInFreeText() {
        Object result = dispatcher.handle(
                "run /summarize on the file at /home/user/example.txt: some content");

        assertThat(result).isInstanceOf(String.class);
        assertThat((String) result).contains(SUMMARIZE_MARKER);
    }

    @Test
    void dispatcherReportsWhenNoKnownSkillMentioned() {
        Object result = dispatcher.handle("please review this: some code");

        assertThat(result).isInstanceOf(String.class);
        assertThat((String) result).contains("No known /skillname mentioned");
    }
}
