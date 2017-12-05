package org.briarproject.bramble.db;

import org.briarproject.bramble.api.crypto.SecretKey;
import org.briarproject.bramble.test.TestUtils;
import org.briarproject.bramble.util.StringUtils;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class BasicHyperSqlTest extends BasicDatabaseTest {

	private final SecretKey key = TestUtils.getSecretKey();

	@Override
	protected String getBinaryType() {
		return "BINARY(32)";
	}

	@Override
	protected String getDriverName() {
		return "org.hsqldb.jdbc.JDBCDriver";
	}

	@Override
	protected Connection openConnection(File db, boolean encrypt)
			throws SQLException {
		String url = "jdbc:hsqldb:file:" + db.getAbsolutePath() +
				";sql.enforce_size=false;allow_empty_batch=true";
		if (encrypt) {
			String hex = StringUtils.toHexString(key.getBytes());
			url += ";encrypt_lobs=true;crypt_type=AES;crypt_key=" + hex;
		}
		return DriverManager.getConnection(url);
	}

	@Override
	protected void shutdownDatabase(File db, boolean encrypt)
			throws SQLException {
		Connection c = openConnection(db, encrypt);
		Statement s = c.createStatement();
		s.executeQuery("SHUTDOWN");
		s.close();
		c.close();
	}
}
