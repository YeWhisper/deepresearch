package com.alibaba.cloud.ai.example.deepresearch.controller.graph;

import com.alibaba.cloud.ai.example.deepresearch.model.req.GraphId;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GraphProcessStreamingContentTest {

	private static final String NODE = "reporter_llm_stream";

	@Test
	void textlessFramesDoNotInterruptTextAndFinishEvents() throws Exception {
		var reasoning = new ChatResponse(List.of(new Generation(
				new AssistantMessage(null, Map.of("reasoning_content", "thinking")))));
		var text = new ChatResponse(List.of(new Generation(new AssistantMessage("正文"))));
		var finish = new ChatResponse(List.of(new Generation(new AssistantMessage(null),
				ChatGenerationMetadata.builder().finishReason("stop").build())));
		var events = process(new StreamingOutput(reasoning, NODE, state()),
				new StreamingOutput(text, NODE, state()), new StreamingOutput(finish, NODE, state()));
		assertThat(events).hasSize(3);
		assertThat(events.get(0).get(NODE).asText()).isEmpty();
		assertThat(events.get(1).get(NODE).asText()).isEqualTo("正文");
		assertThat(events.get(2).get(NODE).asText()).isEmpty();
		assertThat(events.get(2).get("finishReason").asText()).isEqualTo("stop");
		assertThat(events.get(2).get("visible").asBoolean()).isTrue();
	}

	@Test
	void responseWithoutGenerationsProducesEmptyText() throws Exception {
		var events = process(new StreamingOutput(new ChatResponse(List.of()), NODE, state()));
		assertThat(events).hasSize(1);
		assertThat(events.get(0).get(NODE).asText()).isEmpty();
	}

	@Test
	void stringChunksRemainUnchanged() throws Exception {
		var events = process(new StreamingOutput("普通分片", NODE, state()));
		assertThat(events.get(0).get(NODE).asText()).isEqualTo("普通分片");
	}

	private OverAllState state() {
		return new OverAllState(Map.of());
	}

	private List<JsonNode> process(NodeOutput... outputs) throws Exception {
		var process = new GraphProcess(mock(CompiledGraph.class));
		var sink = Sinks.many().unicast().<ServerSentEvent<String>>onBackpressureBuffer();
		try {
			process.processStream(new GraphId("test-session", "test-graph"), Flux.just(outputs), sink);
			var events = sink.asFlux().collectList().block(Duration.ofSeconds(3));
			var mapper = new ObjectMapper();
			var result = new java.util.ArrayList<JsonNode>();
			for (var event : events) {
				result.add(mapper.readTree(event.data()));
			}
			return result;
		}
		finally {
			((ExecutorService) ReflectionTestUtils.getField(process, "executor")).shutdownNow();
		}
	}
}
