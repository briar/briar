package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.EventExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.file.RemovableDriveTask;

import java.io.File;
import java.util.concurrent.Executor;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

@Immutable
@NotNullByDefault
class RemovableDriveTaskFactoryImpl implements RemovableDriveTaskFactory {

	private final Executor eventExecutor;

	@Inject
	RemovableDriveTaskFactoryImpl(@EventExecutor Executor eventExecutor) {
		this.eventExecutor = eventExecutor;
	}

	@Override
	public RemovableDriveTask createReader(RemovableDriveTaskRegistry registry,
			ContactId c, File f) {
		return new RemovableDriverReaderTask(eventExecutor, registry, c, f);
	}

	@Override
	public RemovableDriveTask createWriter(RemovableDriveTaskRegistry registry,
			ContactId c, File f) {
		return new RemovableDriverWriterTask(eventExecutor, registry, c, f);
	}
}
