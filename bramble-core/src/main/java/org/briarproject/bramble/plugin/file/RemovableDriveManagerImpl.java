package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.event.EventExecutor;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.file.RemovableDriveManager;
import org.briarproject.bramble.api.plugin.file.RemovableDriveTask;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import javax.inject.Inject;

@ThreadSafe
@NotNullByDefault
class RemovableDriveManagerImpl
		implements RemovableDriveManager, RemovableDriveTaskRegistry {

	private final Executor ioExecutor, eventExecutor;
	private final ConcurrentHashMap<ContactId, RemovableDriveTask>
			readers = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<ContactId, RemovableDriveTask>
			writers = new ConcurrentHashMap<>();

	@Inject
	RemovableDriveManagerImpl(@IoExecutor Executor ioExecutor,
			@EventExecutor Executor eventExecutor) {
		this.ioExecutor = ioExecutor;
		this.eventExecutor = eventExecutor;
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
	public RemovableDriveTask startReaderTask(ContactId c, File f) {
		RemovableDriverReaderTask task =
				new RemovableDriverReaderTask(eventExecutor, this, c, f);
		RemovableDriveTask old = readers.putIfAbsent(c, task);
		if (old == null) {
			ioExecutor.execute(task);
			return task;
		} else {
			return old;
		}
	}

	@Override
	public RemovableDriveTask startWriterTask(ContactId c, File f) {
		RemovableDriverWriterTask task =
				new RemovableDriverWriterTask(eventExecutor, this, c, f);
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
