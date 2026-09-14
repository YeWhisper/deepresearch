package com.alibaba.cloud.ai.example.deepresearch.agents;

import com.alibaba.cloud.ai.example.deepresearch.repository.ModelParamRepository;
import com.alibaba.cloud.ai.example.deepresearch.repository.ModelParamRepositoryImpl.AgentModel;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekConnectionProperties;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AgentModelsConfigurationTest {

	@Test
	void agentReadsChunkedCompletionUsingConfiguredEndpointAndRequestFactory() throws Exception {
		var requestPath = new AtomicReference<String>();
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/chat/completions", exchange -> {
			requestPath.set(exchange.getRequestURI().getPath());
			exchange.getRequestBody().readAllBytes();
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, 0);
			try (var body = exchange.getResponseBody()) {
				body.write('{');
				body.flush();
				body.write("\"id\":\"test\",\"choices\":[{\"index\":0,\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"OK\"}}]}"
					.getBytes(StandardCharsets.UTF_8));
			}
		});
		server.start();
		var factory = new HttpComponentsClientHttpRequestFactory();
		factory.setConnectTimeout(2000);
		factory.setReadTimeout(2000);
		try {
			var properties = new DeepSeekConnectionProperties();
			properties.setApiKey("test-only");
			properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
			ModelParamRepository repository = () -> List.of(new AgentModel("coordinator", "deepseek-flash"));
			var beans = new DefaultListableBeanFactory();
			var configuration = new AgentModelsConfiguration(repository, beans, properties,
					ToolCallingManager.builder().build(), factory);
			configuration.afterPropertiesSet();
			var client = beans.getBean("coordinatorChatClientBuilder", ChatClient.Builder.class).build();
			assertThat(client.prompt().user("Reply OK.").call().content()).isEqualTo("OK");
			assertThat(requestPath.get()).isEqualTo("/chat/completions");
		}
		finally {
			factory.destroy();
			server.stop(0);
		}
	}
}
