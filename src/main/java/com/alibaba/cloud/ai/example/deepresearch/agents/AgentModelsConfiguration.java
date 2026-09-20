/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.alibaba.cloud.ai.example.deepresearch.agents;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.example.deepresearch.repository.ModelParamRepository;
import com.alibaba.cloud.ai.example.deepresearch.repository.ModelParamRepositoryImpl;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekConnectionProperties;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * This configuration class will configure the corresponding ChatClientBuilder tool according to the `model-config.json`
 * file to provide multi-agent capabilities for deepsearch. different {@link ChatClient.Builder} will be created
 * according to the name column. for example: `researchChatClientBuilder`
 *
 * @author ViliamSun
 * @since 0.1.0
 */
@Configuration
public class AgentModelsConfiguration implements InitializingBean {

    private static final String BEAN_NAME_SUFFIX = "ChatClientBuilder";

    private final List<ModelParamRepositoryImpl.AgentModel> models;

    private final DeepSeekConnectionProperties commonProperties;

    private final ToolCallingManager toolCallingManager;

    private final ClientHttpRequestFactory clientHttpRequestFactory;

    private final BiConsumer<String, DeepSeekChatModel> registerConsumer;

    public AgentModelsConfiguration(ModelParamRepository modelParamRepository, ConfigurableBeanFactory beanFactory,
        DeepSeekConnectionProperties deepSeekConnectionProperties, ToolCallingManager toolCallingManager,
        ClientHttpRequestFactory clientHttpRequestFactory) {
        this.toolCallingManager = toolCallingManager;
        this.clientHttpRequestFactory = clientHttpRequestFactory;
        Assert.notNull(modelParamRepository, "ModelParamRepository must not be null");
        this.commonProperties = deepSeekConnectionProperties;
        // load models from the repository
        this.models = modelParamRepository.loadModels();
        // 灵活地按需注册，不必在启动时静态定义所有 Bean
        this.registerConsumer = (key, value) -> beanFactory.registerSingleton(key.concat(BEAN_NAME_SUFFIX),
            ChatClient.create(value).mutate());
    }

    /**
     * Creates a map of agent model names to their corresponding {@link DeepSeekChatModel} instances.
     *
     * @return a map where the key is the model name and the value is the {@link DeepSeekChatModel} instance.
     */
    private Map<String, DeepSeekChatModel> agentModels() {
        // Convert AgentModel to DeepSeekChatModel
        return models.stream().filter(Objects::nonNull)
            .collect(Collectors.toMap(ModelParamRepositoryImpl.AgentModel::name,
                model -> DeepSeekChatModel.builder()
                    .deepSeekApi(DeepSeekApi.builder().apiKey(commonProperties.getApiKey())
                        .baseUrl(commonProperties.getBaseUrl())
                        // Read the complete non-streaming response before Jackson parses it.
                        .restClientBuilder(RestClient.builder()
                            .requestFactory(new BufferingClientHttpRequestFactory(clientHttpRequestFactory)))
                        .build())
                    .toolCallingManager(toolCallingManager)
                    .defaultOptions(DeepSeekChatOptions.builder().model(model.modelName())
                        .temperature(DashScopeChatModel.DEFAULT_TEMPERATURE).build())
                    .build(),
                (existing, replacement) -> existing) // On duplicate key, keep the)
            );
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.agentModels().forEach(registerConsumer);
    }
}
