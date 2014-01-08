package org.briarproject.system;

import org.briarproject.api.system.Clock;
import org.briarproject.api.system.SystemClock;
import org.briarproject.api.system.SystemTimer;
import org.briarproject.api.system.Timer;

import com.google.inject.AbstractModule;

public class SystemModule extends AbstractModule {

	protected void configure() {
		bind(Clock.class).to(SystemClock.class);
		bind(Timer.class).to(SystemTimer.class);
	}
}
