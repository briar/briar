package org.briarproject.bramble.reporting;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.reporting.DevConfig;
import org.briarproject.bramble.api.reporting.DevReporter;
import org.briarproject.bramble.util.IoUtils;
import org.briarproject.bramble.util.StringUtils;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.logging.Logger;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.net.SocketFactory;

import static java.util.logging.Level.WARNING;

@Immutable
@NotNullByDefault
class DevReporterImpl implements DevReporter {

	private static final Logger LOG =
			Logger.getLogger(DevReporterImpl.class.getName());

	private static final int SOCKET_TIMEOUT = 30 * 1000; // 30 seconds
	private static final int LINE_LENGTH = 70;

	private final CryptoComponent crypto;
	private final DevConfig devConfig;
	private final SocketFactory torSocketFactory;

	DevReporterImpl(CryptoComponent crypto, DevConfig devConfig,
			SocketFactory torSocketFactory) {
		this.crypto = crypto;
		this.devConfig = devConfig;
		this.torSocketFactory = torSocketFactory;
	}

	private Socket connectToDevelopers() throws IOException {
		String onion = devConfig.getDevOnionAddress();
		Socket s = null;
		try {
			s = torSocketFactory.createSocket(onion, 80);
			s.setSoTimeout(SOCKET_TIMEOUT);
			return s;
		} catch (IOException e) {
			tryToClose(s);
			throw e;
		}
	}

	@Override
	public void encryptReportToFile(File reportDir, String filename,
			String report) throws FileNotFoundException {
		byte[] plaintext = StringUtils.toUtf8(report);
		byte[] ciphertext = crypto.encryptToKey(devConfig.getDevPublicKey(),
				plaintext);
		String armoured = crypto.asciiArmour(ciphertext, LINE_LENGTH);

		File f = new File(reportDir, filename);
		PrintWriter writer = null;
		try {
			writer = new PrintWriter(
					new OutputStreamWriter(new FileOutputStream(f)));
			writer.append(armoured);
			writer.flush();
		} finally {
			if (writer != null)
				writer.close();
		}
	}

	@Override
	public void sendReports(File reportDir) {
		File[] reports = reportDir.listFiles();
		if (reports == null || reports.length == 0)
			return; // No reports to send

		LOG.info("Sending reports to developers");
		for (File f : reports) {
			OutputStream out = null;
			InputStream in = null;
			try {
				Socket s = connectToDevelopers();
				out = s.getOutputStream();
				in = new FileInputStream(f);
				IoUtils.copyAndClose(in, out);
				f.delete();
			} catch (IOException e) {
				LOG.log(WARNING, "Failed to send reports", e);
				tryToClose(out);
				tryToClose(in);
				return;
			}
		}
		LOG.info("Reports sent");
	}

	private void tryToClose(@Nullable Closeable c) {
		try {
			if (c != null) c.close();
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}

	private void tryToClose(@Nullable Socket s) {
		try {
			if (s != null) s.close();
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}
}
