package org.briarproject.bramble.reporting;

import org.briarproject.bramble.api.crypto.CryptoComponent;
import org.briarproject.bramble.api.event.Event;
import org.briarproject.bramble.api.event.EventListener;
import org.briarproject.bramble.api.lifecycle.IoExecutor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.TorConstants;
import org.briarproject.bramble.api.plugin.event.TransportActiveEvent;
import org.briarproject.bramble.api.reporting.DevConfig;
import org.briarproject.bramble.api.reporting.DevReporter;
import org.briarproject.bramble.util.IoUtils;
import org.briarproject.bramble.util.StringUtils;

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
import java.util.concurrent.Executor;
import java.util.logging.Logger;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;
import javax.net.SocketFactory;

import static java.util.logging.Level.WARNING;
import static org.briarproject.bramble.util.IoUtils.tryToClose;

@Immutable
@NotNullByDefault
class DevReporterImpl implements DevReporter, EventListener {

	private static final Logger LOG =
			Logger.getLogger(DevReporterImpl.class.getName());

	private static final int SOCKET_TIMEOUT = 30 * 1000; // 30 seconds
	private static final int LINE_LENGTH = 70;

	private final Executor ioExecutor;
	private final CryptoComponent crypto;
	private final DevConfig devConfig;
	private final SocketFactory torSocketFactory;

	@Inject
	DevReporterImpl(@IoExecutor Executor ioExecutor, CryptoComponent crypto,
			DevConfig devConfig, SocketFactory torSocketFactory) {
		this.ioExecutor = ioExecutor;
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
			tryToClose(s, LOG, WARNING);
			throw e;
		}
	}

	@Override
	public void encryptReportToFile(File reportDir, String filename,
			String report) throws FileNotFoundException {
		LOG.info("Encrypting report to file");
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
			tryToClose(writer, LOG, WARNING);
		}
	}

	@Override
	public void eventOccurred(Event e) {
		if (e instanceof TransportActiveEvent) {
			TransportActiveEvent t = (TransportActiveEvent) e;
			if (t.getTransportId().equals(TorConstants.ID))
				ioExecutor.execute(this::sendReports);
		}
	}

	@Override
	public void sendReports() {
		File reportDir = devConfig.getReportDir();
		File[] reports = reportDir.listFiles();
		if (reports == null || reports.length == 0)
			return; // No reports to send

		LOG.info("Sending reports to developers");
		for (File f : reports) {
			OutputStream out = null;
			InputStream in = null;
			try {
				Socket s = connectToDevelopers();
				out = IoUtils.getOutputStream(s);
				in = new FileInputStream(f);
				IoUtils.copyAndClose(in, out);
				f.delete();
			} catch (IOException e) {
				LOG.log(WARNING, "Failed to send reports", e);
				tryToClose(out, LOG, WARNING);
				tryToClose(in, LOG, WARNING);
				return;
			}
		}
		LOG.info("Reports sent");
	}
}
