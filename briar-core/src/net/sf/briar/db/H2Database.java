package net.sf.briar.db;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Properties;

import net.sf.briar.api.clock.Clock;
import net.sf.briar.api.db.DatabaseConfig;
import net.sf.briar.api.db.DbException;
import net.sf.briar.util.FileUtils;

import com.google.inject.Inject;

/** Contains all the H2-specific code for the database. */
class H2Database extends JdbcDatabase {

	private static final String HASH_TYPE = "BINARY(48)";
	private static final String BINARY_TYPE = "BINARY";
	private static final String COUNTER_TYPE = "INT NOT NULL AUTO_INCREMENT";
	private static final String SECRET_TYPE = "BINARY(32)";

	private final File home;
	private final String url;
	private final char[] password;
	private final long maxSize;

	@Inject
	H2Database(DatabaseConfig config, Clock clock) {
		super(HASH_TYPE, BINARY_TYPE, COUNTER_TYPE, SECRET_TYPE, clock);
		home = new File(config.getDataDirectory(), "db");
		url = "jdbc:h2:split:" + home.getPath()
				+ ";CIPHER=AES;MULTI_THREADED=1;DB_CLOSE_ON_EXIT=false";
		password = config.getPassword();
		maxSize = config.getMaxSize();
	}

	public void open(boolean resume) throws DbException, IOException {
		super.open(resume, home.getParentFile(), "org.h2.Driver");
	}

	public void close() throws DbException {
		// H2 will close the database when the last connection closes
		try {
			super.closeAllConnections();
		} catch(SQLException e) {
			throw new DbException(e);
		}
	}

	public long getFreeSpace() throws DbException {
		try {
			File dir = home.getParentFile();
			long free = FileUtils.getFreeSpace(dir);
			long used = getDiskSpace(dir);
			long quota = maxSize - used;
			long min =  Math.min(free, quota);
			return min;
		} catch(IOException e) {
			throw new DbException(e);
		}
	}

	private long getDiskSpace(File f) {
		long total = 0;
		if(f.isDirectory()) {
			for(File child : f.listFiles()) total += getDiskSpace(child);
			return total;
		} else return f.length();
	}

	@Override
	protected Connection createConnection() throws SQLException {
		char[] passwordCopy = password.clone();
		Properties props = new Properties();
		props.setProperty("user", "user");
		props.put("password", passwordCopy);
		try {
			return DriverManager.getConnection(url, props);
		} finally {
			Arrays.fill(passwordCopy, (char) 0);
		}
	}
}
