package net.sf.briar.system;

import net.sf.briar.api.system.Clock;
import net.sf.briar.api.system.SystemClock;
import net.sf.briar.api.system.SystemTimer;
import net.sf.briar.api.system.Timer;

import com.google.inject.AbstractModule;

public class ClockModule extends AbstractModule {

	protected void configure() {
		bind(Clock.class).to(SystemClock.class);
		bind(Timer.class).to(SystemTimer.class);
	}
}
