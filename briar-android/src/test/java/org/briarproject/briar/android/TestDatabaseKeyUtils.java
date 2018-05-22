package org.briarproject.briar.android;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import javax.annotation.Nullable;

import static junit.framework.Assert.assertTrue;

@NotNullByDefault
public class TestDatabaseKeyUtils {

	public static void storeDatabaseKey(File f, String hex) throws IOException {
		f.getParentFile().mkdirs();
		FileOutputStream out = new FileOutputStream(f);
		out.write(hex.getBytes("UTF-8"));
		out.flush();
		out.close();
	}

	@Nullable
	public static String loadDatabaseKey(File f) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				new FileInputStream(f), "UTF-8"));
		String hex = reader.readLine();
		reader.close();
		return hex;
	}
}
