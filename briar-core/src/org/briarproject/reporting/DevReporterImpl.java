package org.briarproject.reporting;

import net.sourceforge.jsocks.socks.Socks5Proxy;
import net.sourceforge.jsocks.socks.SocksException;
import net.sourceforge.jsocks.socks.SocksSocket;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.reporting.DevConfig;
import org.briarproject.api.reporting.DevReporter;
import org.briarproject.util.IoUtils;
import org.briarproject.util.StringUtils;

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
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

class DevReporterImpl implements DevReporter {

	private static final Logger LOG =
			Logger.getLogger(DevReporterImpl.class.getName());

	private static final int SOCKET_TIMEOUT = 30 * 1000; // 30 seconds
	private static final int LINE_LENGTH = 70;

	private CryptoComponent crypto;
	private DevConfig devConfig;

	public DevReporterImpl(CryptoComponent crypto, DevConfig devConfig) {
		this.crypto = crypto;
		this.devConfig = devConfig;
	}

	private Socket connectToDevelopers(int socksPort)
			throws UnknownHostException, SocksException, SocketException {
		Socks5Proxy proxy = new Socks5Proxy("127.0.0.1", socksPort);
		proxy.resolveAddrLocally(false);
		Socket s = new SocksSocket(proxy, devConfig.getDevOnionAddress(), 80);
		s.setSoTimeout(SOCKET_TIMEOUT);
		return s;
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
	public void sendReports(File reportDir, int socksPort) {
		File[] reports = reportDir.listFiles();
		if (reports == null || reports.length == 0)
			return; // No reports to send

		LOG.info("Sending reports to developers");
		for (File f : reports) {
			OutputStream out = null;
			InputStream in = null;
			try {
				Socket s = connectToDevelopers(socksPort);
				out = s.getOutputStream();
				in = new FileInputStream(f);
				IoUtils.copy(in, out);
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

	private void tryToClose(Closeable c) {
		try {
			if (c != null) c.close();
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING)) LOG.log(WARNING, e.toString(), e);
		}
	}
}
