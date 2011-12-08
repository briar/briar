package net.sf.briar.plugins;

import java.io.IOException;
import java.util.concurrent.Executor;

import net.sf.briar.api.plugins.Plugin;
import net.sf.briar.api.plugins.PluginExecutor;

public abstract class AbstractPlugin implements Plugin {

	protected final Executor pluginExecutor;

	protected boolean running = false; // Locking: this

	protected AbstractPlugin(@PluginExecutor Executor pluginExecutor) {
		this.pluginExecutor = pluginExecutor;
	}

	public synchronized void start() throws IOException {
		if(running) throw new IllegalStateException();
		running = true;
	}

	public synchronized void stop() throws IOException {
		if(!running) throw new IllegalStateException();
		running = false;
	}
}
