package com.leadlens.support;

import java.nio.channels.Selector;

/**
 * Whether this machine can create an NIO selector.
 *
 * <p>Sounds exotic; is not. Tomcat's connector and the JDK's HttpClient both need one, and on
 * Windows creating a selector requires an authenticated loopback socket pair that some
 * security software silently breaks. When that happens, {@code Selector.open()} throws
 * "Unable to establish loopback connection" and no embedded web server can start - while plain
 * loopback sockets keep working, which makes it a confusing thing to diagnose from a stack
 * trace three layers down in Catalina.
 *
 * <p>Probed once. The result cannot change within a JVM run, and probing per test would be
 * both slow and pointless.
 */
public final class LoopbackNetworking {

	private static final boolean AVAILABLE = probe();

	private LoopbackNetworking() {
	}

	public static boolean isAvailable() {
		return AVAILABLE;
	}

	private static boolean probe() {
		try (Selector selector = Selector.open()) {
			return selector.isOpen();
		} catch (Throwable t) {
			return false;
		}
	}
}
