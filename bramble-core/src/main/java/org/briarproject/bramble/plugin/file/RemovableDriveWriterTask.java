package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.connection.ConnectionManager;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.EventBus;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.PluginManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.IoUtils.tryToClose;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
class RemovableDriveWriterTask extends RemovableDriveTaskImpl {

	private static final Logger LOG =
			getLogger(RemovableDriveWriterTask.class.getName());

	RemovableDriveWriterTask(
			Executor eventExecutor,
			PluginManager pluginManager,
			ConnectionManager connectionManager,
			EventBus eventBus,
			RemovableDriveTaskRegistry registry,
			ContactId contactId,
			File file) {
		super(eventExecutor, pluginManager, connectionManager, eventBus,
				registry, contactId, file);
	}

	@Override
	public void run() {
		// TODO
		OutputStream out = null;
		try {
			visitObservers(o -> o.onProgress(0, 100));
			out = new FileOutputStream(file);
			visitObservers(o -> o.onCompletion(true));
		} catch (IOException e) {
			logException(LOG, WARNING, e);
			visitObservers(o -> o.onCompletion(false));
		} finally {
			tryToClose(out, LOG, WARNING);
			registry.removeWriter(contactId, this);
		}
	}
}
