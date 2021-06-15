package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.file.RemovableDriveManager;
import org.briarproject.bramble.api.plugin.file.RemovableDriveTask;
import org.briarproject.bramble.api.properties.TransportProperties;

import java.util.concurrent.Executor;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

@ThreadSafe
@NotNullByDefault
class RemovableDriveManagerImpl
		implements RemovableDriveManager, RemovableDriveTaskRegistry {

	private final Executor ioExecutor;
	private final RemovableDriveTaskFactory taskFactory;
	private final Object lock = new Object();

	@GuardedBy("lock")
	private RemovableDriveTask reader = null;
	@GuardedBy("lock")
	private RemovableDriveTask writer = null;

	@Inject
	RemovableDriveManagerImpl(@IoExecutor Executor ioExecutor,
			RemovableDriveTaskFactory taskFactory) {
		this.ioExecutor = ioExecutor;
		this.taskFactory = taskFactory;
	}

	@Nullable
	@Override
	public RemovableDriveTask getCurrentReaderTask() {
		synchronized (lock) {
			return reader;
		}
	}

	@Nullable
	@Override
	public RemovableDriveTask getCurrentWriterTask() {
		synchronized (lock) {
			return writer;
		}
	}

	@Override
	public RemovableDriveTask startReaderTask(TransportProperties p) {
		RemovableDriveTask created;
		synchronized (lock) {
			if (reader != null) return reader;
			reader = created = taskFactory.createReader(this, p);
		}
		ioExecutor.execute(created);
		return created;
	}

	@Override
	public RemovableDriveTask startWriterTask(ContactId c,
			TransportProperties p) {
		RemovableDriveTask created;
		synchronized (lock) {
			if (writer != null) return writer;
			writer = created = taskFactory.createWriter(this, c, p);
		}
		ioExecutor.execute(created);
		return created;
	}

	@Override
	public void removeReader(RemovableDriveTask task) {
		synchronized (lock) {
			if (reader == task) reader = null;
		}
	}

	@Override
	public void removeWriter(RemovableDriveTask task) {
		synchronized (lock) {
			if (writer == task) writer = null;
		}
	}
}
