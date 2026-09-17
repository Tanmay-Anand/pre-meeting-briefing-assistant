package com.leadlens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * LeadLens - AI pre-meeting briefing assistant.
 *
 * <p>See IMPLEMENTATION_PLAN.md for architecture and phases. The governing rule throughout:
 * the model is never trusted to originate a fact.
 */import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;

// Persistence isn't wired up yet (no datasource is configured) — these mock
// endpoints don't need a database, so JPA/datasource autoconfiguration is
// excluded to let the app boot without one. Remove this exclusion once a
// real datasource is configured.
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class })
@ConfigurationPropertiesScan
public class LeadlensApplication {

	public static void main(String[] args) {
		SpringApplication.run(LeadlensApplication.class, args);
	}

}
