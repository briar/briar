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
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.inject.Inject;

import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static java.util.logging.Logger.getLogger;
import static org.briarproject.bramble.db.JdbcUtils.tryToClose;
import static org.briarproject.bramble.util.IoUtils.isNonEmptyDirectory;
import static org.briarproject.bramble.util.LogUtils.logException;
import static org.briarproject.bramble.util.LogUtils.logFileOrDir;

/**
 * Contains all the H2-specific code for the database.
 */
@NotNullByDefault
class H2Database extends JdbcDatabase {

	private static final Logger LOG = getLogger(H2Database.class.getName());

	private static final String HASH_TYPE = "BINARY(32)";
	private static final String SECRET_TYPE = "BINARY(32)";
	private static final String BINARY_TYPE = "BINARY";
	private static final String COUNTER_TYPE = "INT NOT NULL AUTO_INCREMENT";
	private static final String STRING_TYPE = "VARCHAR";
	private static final DatabaseTypes dbTypes = new DatabaseTypes(HASH_TYPE,
			SECRET_TYPE, BINARY_TYPE, COUNTER_TYPE, STRING_TYPE);

	private final DatabaseConfig config;
	private final String url;

	@Nullable
	private volatile SecretKey key = null;

	@Inject
	H2Database(DatabaseConfig config, MessageFactory messageFactory,
			Clock clock) {
		super(dbTypes, messageFactory, clock);
		this.config = config;
		File dir = config.getDatabaseDirectory();
		String path = new File(dir, "db").getAbsolutePath();
		url = "jdbc:h2:split:" + path + ";CIPHER=AES;MULTI_THREADED=1"
				+ ";WRITE_DELAY=0";
	}

	@Override
	public boolean open(SecretKey key, @Nullable MigrationListener listener)
			throws DbException {
		this.key = key;
		File dir = config.getDatabaseDirectory();
		if (LOG.isLoggable(INFO)) {
			LOG.info("Contents of account directory before opening DB:");
			logFileOrDir(LOG, INFO, dir.getParentFile());
		}
		boolean reopen = isNonEmptyDirectory(dir);
		if (LOG.isLoggable(INFO)) LOG.info("Reopening DB: " + reopen);
		if (!reopen && dir.mkdirs()) LOG.info("Created database directory");
		super.open("org.h2.Driver", reopen, key, listener);
		if (LOG.isLoggable(INFO)) {
			LOG.info("Contents of account directory after opening DB:");
			logFileOrDir(LOG, INFO, dir.getParentFile());
		}
		return reopen;
	}

	@Override
	public void close() throws DbException {
		// H2 will close the database when the last connection closes
		Connection c = null;
		Statement s = null;
		try {
			c = createConnection();
			closeAllConnections();
			setDirty(c, false);
			s = c.createStatement();
			s.execute("SHUTDOWN COMPACT");
			s.close();
			c.close();
		} catch (SQLException e) {
			tryToClose(s, LOG, WARNING);
			tryToClose(c, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	public void printStats(Connection txn) throws DbException {
		List<String> names = printNames(txn);
		for (String table : names) {
			tryPrintStats(txn, table);
		}
	}

	private List<String> printNames(Connection txn) throws DbException {
		List<String> names = new ArrayList<>();
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql =
					"SELECT table_catalog, table_schema, table_name FROM INFORMATION_SCHEMA.TABLES";
			ps = txn.prepareStatement(sql);
			rs = ps.executeQuery();
			while (rs.next()) {
				String catalog = rs.getString(1);
				String schema = rs.getString(2);
				String name = rs.getString(3);
				LOG.info(
						String.format("Table %s %s %s", catalog, schema, name));
				names.add(schema + "." + name);
			}
			rs.close();
			ps.close();
		} catch (SQLException e) {
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
		return names;
	}

	private void tryPrintStats(Connection txn, String table) {
		try {
			printStats(txn, table);
		} catch (DbException e) {
			logException(LOG, WARNING, e);
		}
	}

	private void printStats(Connection txn, String table) throws DbException {
		PreparedStatement ps = null;
		ResultSet rs = null;
		try {
			String sql = "CALL DISK_SPACE_USED(?)";
			ps = txn.prepareStatement(sql);
			ps.setString(1, table);
			rs = ps.executeQuery();
			if (!rs.next()) {
				rs.close();
				ps.close();
			}
			long size = rs.getLong(1);
			if (rs.next()) throw new DbStateException();
			rs.close();
			ps.close();
			LOG.info(String.format("Size of table %s: %d", table, size));
		} catch (SQLException e) {
			tryToClose(rs, LOG, WARNING);
			tryToClose(ps, LOG, WARNING);
			throw new DbException(e);
		}
	}

	@Override
	protected Connection createConnection() throws DbException, SQLException {
		SecretKey key = this.key;
		if (key == null) throw new DbClosedException();
		Properties props = new Properties();
		props.setProperty("user", "user");
		// Separate the file password from the user password with a space
		String hex = StringUtils.toHexString(key.getBytes());
		props.put("password", hex + " password");
		return DriverManager.getConnection(getUrl(), props);
	}

	String getUrl() {
		return url;
	}

	@Override
	protected void compactAndClose() throws DbException {
		Connection c = null;
		Statement s = null;
		try {
			c = createConnection();
			closeAllConnections();
			s = c.createStatement();
			s.execute("SHUTDOWN COMPACT");
			s.close();
			c.close();
		} catch (SQLException e) {
			tryToClose(s, LOG, WARNING);
			tryToClose(c, LOG, WARNING);
			throw new DbException(e);
		}
	}
}
