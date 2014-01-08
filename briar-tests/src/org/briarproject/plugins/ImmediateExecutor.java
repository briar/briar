package org.briarproject.plugins;

import java.util.concurrent.Executor;

public class ImmediateExecutor implements Executor {

	public void execute(Runnable r) {
		r.run();
	}
}
