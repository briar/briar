package org.briarproject.bramble.api.plugin.file;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;

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
	 * the given file. If a reader task for the contact is already running,
	 * it will be returned and the file argument will be ignored.
	 */
	RemovableDriveTask startReaderTask(ContactId c, File f);

	/**
	 * Starts and returns a writer task for the given contact, writing to
	 * the given file. If a writer task for the contact is already running,
	 * it will be returned and the file argument will be ignored.
	 */
	RemovableDriveTask startWriterTask(ContactId c, File f);
}
