package com.alibaba.cloud.ai.example.deepresearch.util.convert;

import com.alibaba.cloud.ai.graph.OverAllState;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class FluxConverterTest {

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void mergesTextAfterTextlessFramesAndPreservesFinishReason(boolean fullResponse) {
		var result = convert(fullResponse, response(null, ""), response("前半", ""), response(null, ""),
				response("后半", ""), response(null, "stop"));
		assertThat(result.getResult().getOutput().getText()).isEqualTo("前半后半");
		assertThat(result.getResult().getMetadata().getFinishReason()).isEqualTo("stop");
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void singleTextlessFrameCompletesWithEmptyText(boolean fullResponse) {
		var result = convert(fullResponse, response(null, "stop"));
		assertThat(result.getResult().getOutput().getText()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void preservesToolCalls(boolean fullResponse) {
		var toolCall = new AssistantMessage.ToolCall("call-1", "function", "search", "{}");
		var tools = new ChatResponse(List.of(new Generation(new AssistantMessage(null, Map.of(), List.of(toolCall)))));
		var result = convert(fullResponse, response(null, ""), tools);
		assertThat(result.getResult().getOutput().getToolCalls()).containsExactly(toolCall);
	}

	private ChatResponse response(String text, String finishReason) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(text),
				ChatGenerationMetadata.builder().finishReason(finishReason).build())));
	}

	private ChatResponse convert(boolean fullResponse, ChatResponse... responses) {
		var result = new AtomicReference<ChatResponse>();
		var builder = FluxConverter.builder().startingNode("reporter_llm_stream")
			.startingState(new OverAllState(Map.of())).mapResult(response -> {
				result.set(response);
				return Map.of("done", true);
			});
		var flux = fullResponse ? builder.buildWithChatResponse(Flux.just(responses)) : builder.build(Flux.just(responses));
		var events = flux.collectList().block(Duration.ofSeconds(2));
		assertThat(events).hasSize(responses.length + 1);
		assertThat(events.get(events.size() - 1).isDone()).isTrue();
		if (!fullResponse) {
			for (var event : events) {
				if (!event.isDone()) {
					assertThat(event.getOutput().join().chunk()).isNotNull();
				}
			}
		}
		return result.get();
	}
}
