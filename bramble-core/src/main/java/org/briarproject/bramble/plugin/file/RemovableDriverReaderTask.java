package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.util.IoUtils.tryToClose;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
class RemovableDriverReaderTask extends RemovableDriveTaskImpl {

	private final static Logger LOG =
			getLogger(RemovableDriverReaderTask.class.getName());

	RemovableDriverReaderTask(Executor eventExecutor,
			RemovableDriveTaskRegistry registry, ContactId contactId,
			File file) {
		super(eventExecutor, registry, contactId, file);
	}

	@Override
	public void run() {
		// TODO
		InputStream in = null;
		try {
			visitObservers(o -> o.onProgress(0, 100));
			in = new FileInputStream(file);
			visitObservers(o -> o.onCompletion(true));
		} catch (IOException e) {
			logException(LOG, WARNING, e);
			visitObservers(o -> o.onCompletion(false));
		} finally {
			tryToClose(in, LOG, WARNING);
			registry.removeReader(contactId, this);
		}
	}
}
