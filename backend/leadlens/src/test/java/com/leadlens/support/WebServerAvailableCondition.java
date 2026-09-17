package com.leadlens.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/** Backs {@link EnabledIfWebServerCanStart}. */
public class WebServerAvailableCondition implements ExecutionCondition {

	@Override
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
		return LoopbackNetworking.isAvailable()
				? ConditionEvaluationResult.enabled("loopback selectors available")
				: ConditionEvaluationResult.disabled(
						"Selector.open() fails on this machine, so no embedded web server can "
								+ "start. Usually local security software breaking the loopback "
								+ "socket pair. These tests run in CI.");
	}
}
