package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.file.RemovableDriveManager;
import org.briarproject.bramble.api.plugin.file.RemovableDriveTask;
import org.briarproject.bramble.api.properties.TransportProperties;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

@ThreadSafe
@NotNullByDefault
class RemovableDriveManagerImpl
		implements RemovableDriveManager, RemovableDriveTaskRegistry {

	private final Executor ioExecutor;
	private final RemovableDriveTaskFactory taskFactory;
	private final ConcurrentHashMap<ContactId, RemovableDriveTask>
			readers = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<ContactId, RemovableDriveTask>
			writers = new ConcurrentHashMap<>();

	@Inject
	RemovableDriveManagerImpl(@IoExecutor Executor ioExecutor,
			RemovableDriveTaskFactory taskFactory) {
		this.ioExecutor = ioExecutor;
		this.taskFactory = taskFactory;
	}

	@Nullable
	@Override
	public RemovableDriveTask getCurrentReaderTask(ContactId c) {
		return readers.get(c);
	}

	@Nullable
	@Override
	public RemovableDriveTask getCurrentWriterTask(ContactId c) {
		return writers.get(c);
	}

	@Override
	public RemovableDriveTask startReaderTask(ContactId c,
			TransportProperties p) {
		RemovableDriveTask task = taskFactory.createReader(this, c, p);
		RemovableDriveTask old = readers.putIfAbsent(c, task);
		if (old == null) {
			ioExecutor.execute(task);
			return task;
		} else {
			return old;
		}
	}

	@Override
	public RemovableDriveTask startWriterTask(ContactId c,
			TransportProperties p) {
		RemovableDriveTask task = taskFactory.createWriter(this, c, p);
		RemovableDriveTask old = writers.putIfAbsent(c, task);
		if (old == null) {
			ioExecutor.execute(task);
			return task;
		} else {
			return old;
		}
	}

	@Override
	public void removeReader(ContactId c, RemovableDriveTask task) {
		readers.remove(c, task);
	}

	@Override
	public void removeWriter(ContactId c, RemovableDriveTask task) {
		writers.remove(c, task);
	}
}
