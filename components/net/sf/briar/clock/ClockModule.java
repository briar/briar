package net.sf.briar.clock;

import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.clock.SystemClock;

import com.google.inject.AbstractModule;

public class ClockModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(Clock.class).to(SystemClock.class);
	}
}
