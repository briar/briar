package org.briarproject.bramble.db;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.bramble.util.StringUtils;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class BasicH2Test extends BasicDatabaseTest {

	private final SecretKey key = TestUtils.getSecretKey();

	@Override
	protected String getBinaryType() {
		return "BINARY(32)";
	}

	@Override
	protected String getDriverName() {
		return "org.h2.Driver";
	}

	@Override
	protected Connection openConnection(File db, boolean encrypt)
			throws SQLException {
		String url = "jdbc:h2:split:" + db.getAbsolutePath();
		Properties props = new Properties();
		props.setProperty("user", "user");
		if (encrypt) {
			url += ";CIPHER=AES";
			String hex = StringUtils.toHexString(key.getBytes());
			props.setProperty("password", hex + " password");
		}
		return DriverManager.getConnection(url, props);
	}

	@Override
	protected void shutdownDatabase(File db, boolean encrypt)
			throws SQLException {
		// The DB is closed automatically when the connection is closed
	}
}
