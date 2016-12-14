package org.briarproject.bramble.test;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.util.concurrent.Executor;

@NotNullByDefault
public class ImmediateExecutor implements Executor {

	@Override
	public void execute(Runnable r) {
		r.run();
	}
}
