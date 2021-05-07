package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.file.RemovableDriveTask;

@NotNullByDefault
interface RemovableDriveTaskRegistry {

	void removeReader(ContactId c, RemovableDriveTask task);

	void removeWriter(ContactId c, RemovableDriveTask task);
}
