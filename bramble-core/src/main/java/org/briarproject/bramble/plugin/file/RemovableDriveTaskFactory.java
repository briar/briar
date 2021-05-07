package org.briarproject.bramble.plugin.file;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.file.RemovableDriveTask;

import java.io.File;

@NotNullByDefault
interface RemovableDriveTaskFactory {

	RemovableDriveTask createReader(RemovableDriveTaskRegistry registry,
			ContactId c, File f);

	RemovableDriveTask createWriter(RemovableDriveTaskRegistry registry,
			ContactId c, File f);
}
