package org.briarproject.bramble.db;

import org.briarproject.bramble.api.crypto.SecretKey;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

import static org.briarproject.bramble.test.TestUtils.getSecretKey;
import static org.briarproject.bramble.util.StringUtils.toHexString;

public class BasicH2Test extends BasicDatabaseTest {

	private final SecretKey key = getSecretKey();

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
			String hex = toHexString(key.getBytes());
			props.setProperty("password", hex + " password");
		}
		return DriverManager.getConnection(url, props);
	}

	@Override
	protected void shutdownDatabase(File db, boolean encrypt) {
		// The DB is closed automatically when the connection is closed
	}
}
