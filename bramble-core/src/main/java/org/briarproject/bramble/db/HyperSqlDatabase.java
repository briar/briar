package org.briarproject.bramble.db;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.db.DbClosedException;
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
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.db.JdbcUtils.tryToClose;
import static org.briarproject.bramble.util.IoUtils.isNonEmptyDirectory;

/**
 * Contains all the HSQLDB-specific code for the database.
 */
@NotNullByDefault
class HyperSqlDatabase extends JdbcDatabase {

	private static final Logger LOG =
			getLogger(HyperSqlDatabase.class.getName());

	private static final String HASH_TYPE = "BINARY(32)";
	private static final String SECRET_TYPE = "BINARY(32)";
	private static final String BINARY_TYPE = "BINARY";
	private static final String COUNTER_TYPE =
			"INTEGER NOT NULL GENERATED ALWAYS AS IDENTITY(START WITH 1)";
	private static final String STRING_TYPE = "VARCHAR";
	private static final DatabaseTypes dbTypes = new DatabaseTypes(HASH_TYPE,
			SECRET_TYPE, BINARY_TYPE, COUNTER_TYPE, STRING_TYPE);

	private final DatabaseConfig config;
	private final String url;

	@Nullable
	private volatile SecretKey key = null;

	@Inject
	HyperSqlDatabase(DatabaseConfig config, MessageFactory messageFactory,
			Clock clock) {
		super(dbTypes, messageFactory, clock);
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
		File dir = config.getDatabaseDirectory();
		boolean reopen = isNonEmptyDirectory(dir);
		if (LOG.isLoggable(INFO)) LOG.info("Reopening DB: " + reopen);
		if (!reopen && dir.mkdirs()) LOG.info("Created database directory");
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
			setDirty(c, false);
			s = c.createStatement();
			s.executeQuery("SHUTDOWN");
			s.close();
			c.close();
		} catch (SQLException e) {
			tryToClose(s, LOG, WARNING);
			tryToClose(c, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	protected Connection createConnection() throws DbException, SQLException {
		SecretKey key = this.key;
		if (key == null) throw new DbClosedException();
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
			tryToClose(s, LOG, WARNING);
			tryToClose(c, LOG, WARNING);
			throw new DbException(e);
		}
	}
}
