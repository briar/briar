package org.briarproject.bramble.db;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.util.StringUtils;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import javax.inject.Inject;

/**
 * Contains all the H2-specific code for the database.
 */
@NotNullByDefault
class H2Database extends JdbcDatabase {

	private static final String HASH_TYPE = "BINARY(32)";
	private static final String BINARY_TYPE = "BINARY";
	private static final String COUNTER_TYPE = "INT NOT NULL AUTO_INCREMENT";
	private static final String SECRET_TYPE = "BINARY(32)";

	private final DatabaseConfig config;
	private final String url;

	@Inject
	H2Database(DatabaseConfig config, Clock clock) {
		super(HASH_TYPE, BINARY_TYPE, COUNTER_TYPE, SECRET_TYPE, clock);
		this.config = config;
		File dir = config.getDatabaseDirectory();
		String path = new File(dir, "db").getAbsolutePath();
		url = "jdbc:h2:split:" + path + ";CIPHER=AES;MULTI_THREADED=1"
				+ ";WRITE_DELAY=0;DB_CLOSE_ON_EXIT=false";
	}

	@Override
	public boolean open() throws DbException {
		boolean reopen = config.databaseExists();
		if (!reopen) config.getDatabaseDirectory().mkdirs();
		super.open("org.h2.Driver", reopen);
		return reopen;
	}

	@Override
	public void close() throws DbException {
		// H2 will close the database when the last connection closes
		try {
			super.closeAllConnections();
		} catch (SQLException e) {
			throw new DbException(e);
		}
	}

	@Override
	public long getFreeSpace() throws DbException {
		File dir = config.getDatabaseDirectory();
		long maxSize = config.getMaxSize();
		long free = dir.getFreeSpace();
		long used = getDiskSpace(dir);
		long quota = maxSize - used;
		return Math.min(free, quota);
	}

	private long getDiskSpace(File f) {
		if (f.isDirectory()) {
			long total = 0;
			File[] children = f.listFiles();
			if (children != null)
				for (File child : children) total += getDiskSpace(child);
			return total;
		} else if (f.isFile()) {
			return f.length();
		} else {
			return 0;
		}
	}

	@Override
	protected Connection createConnection() throws SQLException {
		SecretKey key = config.getEncryptionKey();
		if (key == null) throw new IllegalStateException();
		Properties props = new Properties();
		props.setProperty("user", "user");
		// Separate the file password from the user password with a space
		String hex = StringUtils.toHexString(key.getBytes());
		props.put("password", hex + " password");
		return DriverManager.getConnection(url, props);
	}
}
