package com.alibaba.cloud.ai.example.deepresearch.config.rag;

import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAgentAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioSpeechAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioTranscriptionAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeEmbeddingAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeImageAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeRerankAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeVideoAutoConfiguration;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.example.deepresearch.agents.AgentModelsConfiguration;
import com.alibaba.cloud.ai.example.deepresearch.controller.RagDataController;
import com.alibaba.cloud.ai.example.deepresearch.config.HttpClientConfiguration;
import com.alibaba.cloud.ai.example.deepresearch.rag.core.DefaultHybridRagProcessor;
import com.alibaba.cloud.ai.example.deepresearch.rag.strategy.RrfFusionStrategy;
import com.alibaba.cloud.ai.example.deepresearch.repository.ModelParamRepository;
import com.alibaba.cloud.ai.example.deepresearch.repository.ModelParamRepositoryImpl;
import com.alibaba.cloud.ai.example.deepresearch.service.VectorStoreDataIngestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RagDisabledConfigurationTest {

	@ParameterizedTest
	@ValueSource(strings = { "simple", "elasticsearch" })
	void disabledRagDoesNotRequireVectorDependencies(String storeType) {
		new ApplicationContextRunner().withUserConfiguration(ScannedRagConfiguration.class)
			.withPropertyValues(RagProperties.RAG_PREFIX + ".enabled=false",
					RagProperties.RAG_PREFIX + ".vector-store-type=" + storeType)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).doesNotHaveBean(VectorStore.class);
				assertThat(context).doesNotHaveBean(VectorStoreDataIngestionService.class);
				assertThat(context).doesNotHaveBean(RagDataController.class);
				assertThat(context).doesNotHaveBean("elasticsearchRestClient");
			});
	}

	@Test
	void enabledSimpleRagStillCreatesIngestionBeans() {
		new ApplicationContextRunner()
			.withUserConfiguration(RagVectorStoreConfiguration.class, VectorStoreDataIngestionService.class,
					RagDataController.class)
			.withBean(EmbeddingModel.class, () -> mock(EmbeddingModel.class))
			.withPropertyValues(RagProperties.RAG_PREFIX + ".enabled=true",
					RagProperties.RAG_PREFIX + ".vector-store-type=simple",
					RagProperties.RAG_PREFIX + ".simple.storage-path=")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasSingleBean(VectorStore.class);
				assertThat(context).hasSingleBean(VectorStoreDataIngestionService.class);
				assertThat(context).hasSingleBean(RagDataController.class);
			});
	}

	@Test
	void applicationConfigurationUsesDashScopeEmbeddingAndDeepSeekAgents() throws Exception {
		var properties = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
			.get(0);
		new ApplicationContextRunner()
			.withUserConfiguration(AgentModelsConfiguration.class, HttpClientConfiguration.class, RagVectorStoreConfiguration.class,
					VectorStoreDataIngestionService.class, RagDataController.class,
					DefaultHybridRagProcessor.class, RrfFusionStrategy.class)
			.withBean(ModelParamRepository.class, () -> new ModelParamRepositoryImpl(
					new ClassPathResource("model-config.json"), new ObjectMapper()))
			.withConfiguration(AutoConfigurations.of(DashScopeAgentAutoConfiguration.class,
					DashScopeChatAutoConfiguration.class, DashScopeEmbeddingAutoConfiguration.class,
					DashScopeAudioSpeechAutoConfiguration.class, DashScopeAudioTranscriptionAutoConfiguration.class,
					DashScopeImageAutoConfiguration.class, DashScopeVideoAutoConfiguration.class,
					DashScopeRerankAutoConfiguration.class,
					DeepSeekChatAutoConfiguration.class, ChatClientAutoConfiguration.class))
			.withPropertyValues(Stream.of("spring.ai.dashscope.enabled", "spring.ai.dashscope.agent.enabled",
					"spring.ai.model.chat", "spring.ai.model.embedding", "spring.ai.model.image",
					"spring.ai.model.video", "spring.ai.model.rerank", "spring.ai.model.audio.speech",
					"spring.ai.model.audio.transcription", RagProperties.RAG_PREFIX + ".enabled")
				.filter(key -> properties.getProperty(key) != null)
				.map(key -> key + "=" + properties.getProperty(key))
				.toArray(String[]::new))
			.withPropertyValues(
					RagProperties.RAG_PREFIX + ".vector-store-type=simple",
					RagProperties.RAG_PREFIX + ".simple.storage-path=",
					"spring.ai.deepseek.api-key=test-only-deepseek",
					"spring.ai.dashscope.embedding.api-key=test-only-dashscope")
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context).hasBean("researchChatClientBuilder");
				assertThat(context).hasBean("ragChatClientBuilder");
				assertThat(context).doesNotHaveBean("dashscopeAgentApi");
				assertThat(context).doesNotHaveBean("dashscopeChatModel");
				assertThat(context).doesNotHaveBean("dashScopeSpeechSynthesisModel");
				assertThat(context).doesNotHaveBean("dashScopeAudioTranscriptionModel");
				assertThat(context).doesNotHaveBean("dashScopeImageModel");
				assertThat(context).doesNotHaveBean("dashScopeVideoModel");
				assertThat(context).doesNotHaveBean("dashscopeRerankModel");
				assertThat(context).hasSingleBean(ChatModel.class);
				assertThat(context.getBean(ChatModel.class)).isInstanceOf(DeepSeekChatModel.class);
				assertThat(context).hasSingleBean(EmbeddingModel.class);
				assertThat(context.getBean(EmbeddingModel.class)).isInstanceOf(DashScopeEmbeddingModel.class);
				assertThat(context).hasSingleBean(VectorStore.class);
				assertThat(context).hasSingleBean(DefaultHybridRagProcessor.class);
				assertThat(context).doesNotHaveBean("elasticsearchRestClient");
				assertThat(context.getBean(ModelParamRepository.class).loadModels()).allSatisfy(model ->
						assertThat(model.modelName()).isEqualTo(properties.getProperty("spring.ai.deepseek.chat.options.model")));
			});
	}

	@Configuration(proxyBeanMethods = false)
	@EnableConfigurationProperties(RagProperties.class)
	@ComponentScan(basePackageClasses = RagVectorStoreConfiguration.class)
	@Import({ RagDataController.class, VectorStoreDataIngestionService.class })
	static class ScannedRagConfiguration {

	}

}
