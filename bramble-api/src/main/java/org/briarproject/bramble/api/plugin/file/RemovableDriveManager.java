package org.briarproject.bramble.api.plugin.file;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.properties.TransportProperties;

import javax.annotation.Nullable;

@NotNullByDefault
public interface RemovableDriveManager {

	/**
	 * Returns the currently running reader task, or null if no reader task
	 * is running.
	 */
	@Nullable
	RemovableDriveTask getCurrentReaderTask();

	/**
	 * Returns the currently running writer task,  or null if no writer task
	 * is running.
	 */
	@Nullable
	RemovableDriveTask getCurrentWriterTask();

	/**
	 * Starts and returns a reader task, reading from a stream described by
	 * the given transport properties. If a reader task is already running,
	 * it will be returned and the argument will be ignored.
	 */
	RemovableDriveTask startReaderTask(TransportProperties p);

	/**
	 * Starts and returns a writer task for the given contact, writing to
	 * a stream described by the given transport properties. If a writer task
	 * is already running, it will be returned and the arguments will be
	 * ignored.
	 */
	RemovableDriveTask startWriterTask(ContactId c, TransportProperties p);
}
