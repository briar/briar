package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.connection.ConnectionManager;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.event.EventExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginManager;
import org.briarproject.bramble.api.plugin.file.RemovableDriveTask;

import java.io.File;
import java.util.concurrent.Executor;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
class RemovableDriveTaskFactoryImpl implements RemovableDriveTaskFactory {

	private final Executor eventExecutor;
	private final PluginManager pluginManager;
	private final ConnectionManager connectionManager;
	private final EventBus eventBus;

	@Inject
	RemovableDriveTaskFactoryImpl(
			@EventExecutor Executor eventExecutor,
			PluginManager pluginManager,
			ConnectionManager connectionManager,
			EventBus eventBus) {
		this.eventExecutor = eventExecutor;
		this.pluginManager = pluginManager;
		this.connectionManager = connectionManager;
		this.eventBus = eventBus;
	}

	@Override
	public RemovableDriveTask createReader(RemovableDriveTaskRegistry registry,
			ContactId c, File f) {
		return new RemovableDriverReaderTask(eventExecutor, pluginManager,
				connectionManager, eventBus, registry, c, f);
	}

	@Override
	public RemovableDriveTask createWriter(RemovableDriveTaskRegistry registry,
			ContactId c, File f) {
		return new RemovableDriverWriterTask(eventExecutor, pluginManager,
				connectionManager, eventBus, registry, c, f);
	}
}
