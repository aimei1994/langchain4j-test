package com.test.skill;

import com.test.dispatcher.AgentDispatcher;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
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

    // Sleeps a fixed duration per call, so tests can prove multiple skills
    // run concurrently instead of one after another.
    private static class SlowFakeChatModel implements ChatModel {
        private final Duration delay;

        SlowFakeChatModel(Duration delay) {
            this.delay = delay;
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
        }
    }

    // Fails for whichever skill's system message contains failingMarker, so
    // tests can prove one skill's failure doesn't take down the others.
    private static class PartiallyFailingFakeChatModel implements ChatModel {
        private final String failingMarker;

        PartiallyFailingFakeChatModel(String failingMarker) {
            this.failingMarker = failingMarker;
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            String systemText = SystemMessage.findFirst(request.messages())
                    .map(SystemMessage::text)
                    .orElse("");
            if (systemText.contains(failingMarker)) {
                throw new RuntimeException("simulated failure");
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("ok")).build();
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

    @Test
    void invokeWithMultipleSkillNamesRunsThemConcurrentlyNotSequentially() {
        Duration perCallDelay = Duration.ofMillis(300);
        SkillService slowSkillService = new SkillService(
                new SlowFakeChatModel(perCallDelay), "skills", new FileSystemTools());
        slowSkillService.init();

        List<String> threeSkills = List.of("summarize", "weather", "code-repo-review");

        Instant start = Instant.now();
        Map<String, String> results = slowSkillService.invoke(threeSkills, "some input");
        Duration elapsed = Duration.between(start, Instant.now());

        assertThat(results).containsOnlyKeys("summarize", "weather", "code-repo-review");
        // Sequential would take >= 3 * 300ms = 900ms; concurrent should land
        // close to a single call's delay. Generous margin to avoid CI flakiness.
        assertThat(elapsed).isLessThan(Duration.ofMillis(800));
    }

    @Test
    void invokeWithMultipleSkillNamesIsolatesOneSkillsFailure() {
        SkillService partiallyFailingSkillService = new SkillService(
                new PartiallyFailingFakeChatModel(WEATHER_MARKER), "skills", new FileSystemTools());
        partiallyFailingSkillService.init();

        Map<String, String> results = partiallyFailingSkillService.invoke(
                List.of("summarize", "weather"), "some input");

        assertThat(results).containsOnlyKeys("summarize", "weather");
        assertThat(results.get("summarize")).isEqualTo("ok");
        assertThat(results.get("weather")).contains("ERROR");
    }
}
