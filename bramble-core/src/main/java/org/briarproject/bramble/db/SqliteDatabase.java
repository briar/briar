package org.briarproject.bramble.db;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.api.db.DatabaseConfig;
import org.briarproject.bramble.api.db.DbClosedException;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.db.MigrationListener;
import org.briarproject.bramble.api.sync.MessageFactory;
import org.briarproject.bramble.api.system.Clock;
import org.briarproject.nullsafety.NotNullByDefault;
import org.sqlite.mc.SQLiteMCSqlCipherConfig;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.db.JdbcUtils.tryToClose;
import static org.briarproject.bramble.util.IoUtils.isNonEmptyDirectory;

/**
 * Contains all the SQLite-specific code for the database.
 */
@NotNullByDefault
class SqliteDatabase extends JdbcDatabase {

	private static final Logger LOG = getLogger(SqliteDatabase.class.getName());

	private static final String HASH_TYPE = "BLOB";
	private static final String SECRET_TYPE = "BLOB";
	private static final String BINARY_TYPE = "BLOB";
	private static final String COUNTER_TYPE =
			"INTEGER PRIMARY KEY AUTOINCREMENT";
	private static final String STRING_TYPE = "VARCHAR";
	private static final DatabaseTypes dbTypes = new DatabaseTypes(HASH_TYPE,
			SECRET_TYPE, BINARY_TYPE, COUNTER_TYPE, STRING_TYPE);

	private final DatabaseConfig config;
	private final String url;

	@Nullable
	private volatile Properties properties = null;

	@Inject
	SqliteDatabase(DatabaseConfig config, MessageFactory messageFactory,
			Clock clock) {
		super(dbTypes, messageFactory, clock);
		this.config = config;
		File dir = config.getDatabaseDirectory();
		String path = new File(dir, "db").getAbsolutePath();
		url = "jdbc:sqlite:" + path + "?cipher=sqlcipher";
	}

	@Override
	public boolean open(SecretKey key, @Nullable MigrationListener listener)
			throws DbException {
		properties = SQLiteMCSqlCipherConfig.getDefault()
				.withHexKey(key.getBytes())
				.build()
				.toProperties();
		File dir = config.getDatabaseDirectory();
		boolean reopen = isNonEmptyDirectory(dir);
		if (LOG.isLoggable(INFO)) LOG.info("Reopening DB: " + reopen);
		if (!reopen && dir.mkdirs()) LOG.info("Created database directory");
		super.open("org.sqlite.JDBC", reopen, key, listener);
		return reopen;
	}

	@Override
	public void close() throws DbException {
		Connection c = null;
		try {
			c = createConnection();
			setDirty(c, false);
			c.close();
			closeAllConnections();
		} catch (SQLException e) {
			tryToClose(c, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	protected Connection createConnection() throws DbException, SQLException {
		Properties properties = this.properties;
		if (properties == null) throw new DbClosedException();
		Connection c = DriverManager.getConnection(url, properties);
		Statement s = null;
		try {
			s = c.createStatement();
			s.execute("PRAGMA foreign_keys = ON");
			s.close();
		} catch (SQLException e) {
			tryToClose(s, LOG, WARNING);
			tryToClose(c, LOG, WARNING);
			throw new DbException(e);
		}
		return c;
	}

	@Override
	protected void compactAndClose() throws DbException {
		close();
	}
}
