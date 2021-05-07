package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.Consumer;
import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.file.RemovableDriveTask;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
@NotNullByDefault
abstract class RemovableDriveTaskImpl implements RemovableDriveTask {

	private final Executor eventExecutor;
	final RemovableDriveTaskRegistry registry;
	final ContactId contactId;
	final File file;
	private final List<Observer> observers = new CopyOnWriteArrayList<>();

	RemovableDriveTaskImpl(Executor eventExecutor,
			RemovableDriveTaskRegistry registry, ContactId contactId,
			File file) {
		this.contactId = contactId;
		this.file = file;
		this.registry = registry;
		this.eventExecutor = eventExecutor;
	}

	@Override
	public File getFile() {
		return file;
	}

	@Override
	public void addObserver(Observer o) {
		observers.add(o);
	}

	@Override
	public void removeObserver(Observer o) {
		observers.remove(o);
	}

	void visitObservers(Consumer<Observer> visitor) {
		eventExecutor.execute(() -> {
			for (Observer o : observers) visitor.accept(o);
		});
	}
}
