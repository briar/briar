package net.sf.briar.plugins;

import java.io.IOException;
import java.util.concurrent.Executor;

import net.sf.briar.api.plugins.TransportPlugin;

public abstract class AbstractPlugin implements TransportPlugin {

	protected final Executor executor;

	// This field must only be accessed with this's lock held
	protected boolean started = false;

	protected AbstractPlugin(Executor executor) {
		this.executor = executor;
	}

	public synchronized void start() throws IOException {
		if(started) throw new IllegalStateException();
		started = true;
	}

	public synchronized void stop() throws IOException {
		if(!started) throw new IllegalStateException();
		started = false;
	}
}
