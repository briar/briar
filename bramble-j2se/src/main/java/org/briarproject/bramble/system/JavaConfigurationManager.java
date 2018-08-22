package org.briarproject.bramble.system;

import org.briarproject.bramble.api.ConfigurationManager;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.File;
import java.io.IOException;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import javax.inject.Inject;

import static java.nio.file.Files.setPosixFilePermissions;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;
import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
class JavaConfigurationManager implements ConfigurationManager {

	private static final Logger LOG =
			Logger.getLogger(JavaConfigurationManager.class.getName());

	@Inject
	public JavaConfigurationManager() {
		try {
			ensurePermissions(getAppDir());
		} catch (IOException e) {
			logException(LOG, WARNING, e);
		}
	}

	@Override
	public File getAppDir() {
		String home = System.getProperty("user.home");
		return new File(home + File.separator + ".briar");
	}

	private void ensurePermissions(File file)throws IOException {
		if (!file.exists()) {
			if (!file.mkdirs()) {
				throw new IOException("Could not create directory");
			}
		}
		Set<PosixFilePermission> perms = new HashSet<>();
		perms.add(OWNER_READ);
		perms.add(OWNER_WRITE);
		perms.add(OWNER_EXECUTE);
		setPosixFilePermissions(file.toPath(), perms);
	}

}
