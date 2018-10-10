package org.briarproject.bramble.db;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.MigrationListener;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.bramble.util.StringUtils;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Contains all the HSQLDB-specific code for the database.
 */
@NotNullByDefault
class HyperSqlDatabase extends JdbcDatabase {

	private static final String HASH_TYPE = "BINARY(32)";
	private static final String SECRET_TYPE = "BINARY(32)";
	private static final String BINARY_TYPE = "BINARY";
	private static final String COUNTER_TYPE =
			"INTEGER NOT NULL GENERATED ALWAYS AS IDENTITY(START WITH 1)";
	private static final String STRING_TYPE = "VARCHAR";

	private final DatabaseConfig config;
	private final String url;

	@Nullable
	private volatile SecretKey key = null;

	@Inject
	HyperSqlDatabase(DatabaseConfig config, MessageFactory messageFactory,
			Clock clock) {
		super(HASH_TYPE, SECRET_TYPE, BINARY_TYPE, COUNTER_TYPE, STRING_TYPE,
				messageFactory, clock);
		this.config = config;
		File dir = config.getDatabaseDirectory();
		String path = new File(dir, "db").getAbsolutePath();
		url = "jdbc:hsqldb:file:" + path
				+ ";sql.enforce_size=false;allow_empty_batch=true"
				+ ";encrypt_lobs=true;crypt_type=AES";
	}

	@Override
	public boolean open(SecretKey key, @Nullable MigrationListener listener)
			throws DbException {
		this.key = key;
		boolean reopen = !config.getDatabaseDirectory().mkdirs();
		super.open("org.hsqldb.jdbc.JDBCDriver", reopen, key, listener);
		return reopen;
	}

	@Override
	public void close() throws DbException {
		Connection c = null;
		Statement s = null;
		try {
			super.closeAllConnections();
			c = createConnection();
			s = c.createStatement();
			s.executeQuery("SHUTDOWN");
			s.close();
			c.close();
		} catch (SQLException e) {
			tryToClose(s);
			tryToClose(c);
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
		SecretKey key = this.key;
		if (key == null) throw new IllegalStateException();
		String hex = StringUtils.toHexString(key.getBytes());
		return DriverManager.getConnection(url + ";crypt_key=" + hex);
	}

	@Override
	protected void compactAndClose() throws DbException {
		Connection c = null;
		Statement s = null;
		try {
			super.closeAllConnections();
			c = createConnection();
			s = c.createStatement();
			s.executeQuery("SHUTDOWN COMPACT");
			s.close();
			c.close();
		} catch (SQLException e) {
			tryToClose(s);
			tryToClose(c);
			throw new DbException(e);
		}
	}
}
