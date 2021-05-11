package org.briarproject.bramble.api.plugin.file;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.properties.TransportProperties;

import javax.annotation.Nullable;

@NotNullByDefault
public interface RemovableDriveManager {

	/**
	 * Returns the currently running reader task for the given contact,
	 * or null if no task is running.
	 */
	@Nullable
	RemovableDriveTask getCurrentReaderTask(ContactId c);

	/**
	 * Returns the currently running writer task for the given contact,
	 * or null if no task is running.
	 */
	@Nullable
	RemovableDriveTask getCurrentWriterTask(ContactId c);

	/**
	 * Starts and returns a reader task for the given contact, reading from
	 * a stream described by the given transport properties. If a reader task
	 * for the contact is already running, it will be returned and the
	 * transport properties will be ignored.
	 */
	RemovableDriveTask startReaderTask(ContactId c, TransportProperties p);

	/**
	 * Starts and returns a writer task for the given contact, writing to
	 * a stream described by the given transport properties. If a writer task
	 * for the contact is already running, it will be returned and the
	 * transport properties will be ignored.
	 */
	RemovableDriveTask startWriterTask(ContactId c, TransportProperties p);
}
