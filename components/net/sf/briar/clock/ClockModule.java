package net.sf.briar.clock;

import com.google.inject.AbstractModule;

public class ClockModule extends AbstractModule {

	@Override
	protected void configure() {
		bind(Clock.class).to(SystemClock.class);
	}
}
