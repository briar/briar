package net.sf.briar.db;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.briar.api.crypto.Password;
import net.sf.briar.api.db.DatabasePassword;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.protocol.MessageFactory;

import com.google.inject.Inject;

/** Contains all the H2-specific code for the database. */
class H2Database extends JdbcDatabase {

	private static final Logger LOG =
		Logger.getLogger(H2Database.class.getName());

	private final File home;
	private final Password password;
	private final String url;
	private final long maxSize;

	@Inject
	H2Database(File dir, MessageFactory messageFactory,
			@DatabasePassword Password password, long maxSize) {
		super(messageFactory, "BINARY(32)", "BIGINT");
		home = new File(dir, "db");
		this.password = password;
		url = "jdbc:h2:split:" + home.getPath()
		+ ";CIPHER=AES;DB_CLOSE_ON_EXIT=false";
		this.maxSize = maxSize;
	}

	public void open(boolean resume) throws DbException {
		super.open(resume, home.getParentFile(), "org.h2.Driver");
	}

	public void close() throws DbException {
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Closing database");
		try {
			super.closeAllConnections();
		} catch(SQLException e) {
			throw new DbException(e);
		}
	}

	public long getFreeSpace() throws DbException {
		File dir = home.getParentFile();
		long free = dir.getFreeSpace();
		long used = getDiskSpace(dir);
		long quota = maxSize - used;
		long min =  Math.min(free, quota);
		if(LOG.isLoggable(Level.FINE)) LOG.fine("Free space: " + min);
		return min;
	}

	@Override
	protected Connection createConnection() throws SQLException {
		Properties props = new Properties();
		props.setProperty("user", "b");
		char[] passwordArray = password.getPassword();
		props.put("password", passwordArray);
		try {
			return DriverManager.getConnection(url, props);
		} finally {
			Arrays.fill(passwordArray, (char) 0);
		}
	}
}
