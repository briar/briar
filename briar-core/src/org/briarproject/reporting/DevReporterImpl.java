package org.briarproject.reporting;

import com.google.common.io.Files;

import net.sourceforge.jsocks.socks.Socks5Proxy;
import net.sourceforge.jsocks.socks.SocksException;
import net.sourceforge.jsocks.socks.SocksSocket;

import org.briarproject.api.crypto.CryptoComponent;
import org.briarproject.api.reporting.DevConfig;
import org.briarproject.api.reporting.DevReporter;
import org.briarproject.util.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

class DevReporterImpl implements DevReporter {

	private static final Logger LOG =
			Logger.getLogger(DevReporterImpl.class.getName());

	private static final int SOCKET_TIMEOUT = 30 * 1000; // 30 seconds
	private static final String CRLF = "\r\n";

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
		String encryptedReport =
				crypto.encryptToKey(devConfig.getDevPublicKey(),
						StringUtils.toUtf8(report));

		File f = new File(reportDir, filename);
		PrintWriter writer = null;
		try {
			writer = new PrintWriter(
					new OutputStreamWriter(new FileOutputStream(f)));
			writer.append(encryptedReport);
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

		LOG.info("Connecting to developers");
		Socket s;
		try {
			s = connectToDevelopers(socksPort);
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING))
				LOG.log(WARNING, "Could not connect to developers", e);
			return;
		}

		LOG.info("Sending reports to developers");
		OutputStream output;
		PrintWriter writer = null;
		try {
			output = s.getOutputStream();
			writer = new PrintWriter(
					new OutputStreamWriter(output, "UTF-8"), true);
			for (File f : reports) {
				List<String> encryptedReport = Files.readLines(f,
						Charset.forName("UTF-8"));
				writer.append(f.getName()).append(CRLF);
				for (String line : encryptedReport) {
					writer.append(line).append(CRLF);
				}
				writer.append(CRLF);
				writer.flush();
				f.delete();
			}
			LOG.info("Reports sent");
		} catch (IOException e) {
			if (LOG.isLoggable(WARNING))
				LOG.log(WARNING, "Connection to developers failed", e);
		} finally {
			if (writer != null)
				writer.close();
		}
	}
}
