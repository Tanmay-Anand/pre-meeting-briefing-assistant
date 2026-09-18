package com.leadlens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * LeadLens - AI pre-meeting briefing assistant.
 *
 * <p>See IMPLEMENTATION_PLAN.md for architecture and phases. The governing rule throughout:
 * the model is never trusted to originate a fact.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LeadlensApplication {

	public static void main(String[] args) {
		SpringApplication.run(LeadlensApplication.class, args);
	}

}
