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
import net.sf.briar.api.db.DatabaseComponent;
import net.sf.briar.api.db.DatabasePassword;
import net.sf.briar.api.db.DbException;
import net.sf.briar.api.protocol.MessageFactory;
import net.sf.briar.util.FileUtils;

import com.google.inject.Inject;

class H2Database extends JdbcDatabase {

	private static final Logger LOG =
		Logger.getLogger(H2Database.class.getName());

	private final Password password;
	private final File home;
	private final String url;

	@Inject
	H2Database(MessageFactory messageFactory,
			@DatabasePassword Password password) {
		super(messageFactory, "BINARY(32)");
		this.password = password;
		home = new File(FileUtils.getBriarDirectory(), "Data/db/db");
		url = "jdbc:h2:split:" + home.getPath()
		+ ";CIPHER=AES;DB_CLOSE_ON_EXIT=false";
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
		long quota = DatabaseComponent.MAX_DB_SIZE - used;
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
