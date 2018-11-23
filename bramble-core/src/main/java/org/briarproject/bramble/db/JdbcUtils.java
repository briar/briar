package org.briarproject.bramble.db;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import static org.briarproject.bramble.util.LogUtils.logException;

@NotNullByDefault
class JdbcUtils {

	static void tryToClose(@Nullable ResultSet rs, Logger logger, Level level) {
		try {
			if (rs != null) rs.close();
		} catch (SQLException e) {
			logException(logger, level, e);
		}
	}

	static void tryToClose(@Nullable Statement s, Logger logger, Level level) {
		try {
			if (s != null) s.close();
		} catch (SQLException e) {
			logException(logger, level, e);
		}
	}

	static void tryToClose(@Nullable Connection c, Logger logger, Level level) {
		try {
			if (c != null) c.close();
		} catch (SQLException e) {
			logException(logger, level, e);
		}
	}
}
