package com.leadlens.common;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * A bounded executor for briefing generation, and the switch that turns {@code @Async} and
 * {@code @Scheduled} on at all.
 *
 * <p>Bounded deliberately: an unbounded pool lets one tenant's bulk refresh starve every other
 * tenant's generation, which is the multi-tenant version of the single-threaded-executor problem
 * this project's Praxis Chess predecessor hit for a different reason (Appendix 1, "deliberately
 * not taken"). A caller-runs rejection policy means a burst beyond capacity slows down rather
 * than silently drops a request.
 */
@Configuration
@EnableAsync
@EnableScheduling
public class AsyncConfig {

	@Bean("briefingExecutor")
	public ThreadPoolTaskExecutor briefingExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(8);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("briefing-");
		executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		executor.initialize();
		return executor;
	}
}
