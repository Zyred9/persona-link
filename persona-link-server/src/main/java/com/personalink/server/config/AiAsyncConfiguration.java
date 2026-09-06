package com.personalink.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/** AI 题库异步执行配置。 */
@EnableAsync
@Configuration
public class AiAsyncConfiguration {

    /**
     * 提供有界 AI 生成线程池。
     *
     * @return AI 生成执行器
     */
    @Bean("aiQuestionGenerationExecutor")
    public Executor aiQuestionGenerationExecutor() {
        return this.executor("ai-question-", 100);
    }

    /** 独立有界图片任务池，避免图片轮询占满题库生成线程。 */
    @Bean("aiImageGenerationExecutor")
    public Executor aiImageGenerationExecutor() {
        return this.executor("ai-image-", 32);
    }

    private Executor executor(String prefix, int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(prefix);
        executor.initialize();
        return executor;
    }
}
