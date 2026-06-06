package com.serverscout.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class ScanExecutorConfig {

    @Value("${app.scan.max-concurrent-tasks:5}")
    private int maxConcurrentTasks;

    @Bean("scanExecutor")
    public Executor scanExecutor() {
        int corePool = Math.max(2, maxConcurrentTasks);
        int maxPool = Math.max(corePool, maxConcurrentTasks * 2);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePool);
        executor.setMaxPoolSize(maxPool);
        executor.setQueueCapacity(maxPool * 2);
        executor.setThreadNamePrefix("scan-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
