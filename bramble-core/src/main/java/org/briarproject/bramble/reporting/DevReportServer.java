package org.briarproject.bramble.reporting;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.Semaphore;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

@Immutable
@NotNullByDefault
public class DevReportServer {

	private static final String FILE_PREFIX = "report-";
	private static final String FILE_SUFFIX = ".enc";
	private static final int MAX_REPORT_LENGTH = 1024 * 1024;
	private static final int MIN_REQUEST_INTERVAL_MS = 60 * 1000; // 1 minute
	private static final int MAX_TOKENS = 1000;
	private static final int SOCKET_TIMEOUT_MS = 60 * 1000; // 1 minute

	private final InetSocketAddress listenAddress;
	private final File reportDir;

	private DevReportServer(InetSocketAddress listenAddress, File reportDir) {
		this.listenAddress = listenAddress;
		this.reportDir = reportDir;
	}

	private void listen() throws IOException {
		ServerSocket ss = new ServerSocket();
		ss.bind(listenAddress);
		TokenBucket bucket = new TokenBucket();
		bucket.start();
		try {
			while (true) {
				Socket s = ss.accept();
				System.out.println("Incoming connection");
				bucket.waitForToken();
				new ReportSaver(s).start();
			}
		} catch (InterruptedException e) {
			System.err.println("Interrupted while listening");
		} finally {
			tryToClose(ss);
		}
	}

	private void tryToClose(@Nullable ServerSocket ss) {
		try {
			if (ss != null) ss.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void tryToClose(@Nullable Closeable c) {
		try {
			if (c != null) c.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 3) {
			System.err.println("Usage:");
			System.err.println("DevReportServer <addr> <port> <report_dir>");
			System.exit(1);
		}
		int port = Integer.parseInt(args[1]);
		InetSocketAddress listenAddress = new InetSocketAddress(args[0], port);
		File reportDir = new File(args[2]);
		System.out.println("Listening on " + listenAddress);
		System.out.println("Saving reports to " + reportDir);
		new DevReportServer(listenAddress, reportDir).listen();
	}

	private static class TokenBucket extends Thread {

		private final Semaphore semaphore = new Semaphore(MAX_TOKENS);

		private TokenBucket() {
			setDaemon(true);
		}

		private void waitForToken() throws InterruptedException {
			// Wait for a token to become available and remove it
			semaphore.acquire();
		}

		@Override
		public void run() {
			try {
				while (true) {
					// If the bucket isn't full, add a token
					if (semaphore.availablePermits() < MAX_TOKENS) {
						System.out.println("Adding token to bucket");
						semaphore.release();
					}
					Thread.sleep(MIN_REQUEST_INTERVAL_MS);
				}
			} catch (InterruptedException e) {
				System.err.println("Interrupted while sleeping");
			}
		}
	}

	private class ReportSaver extends Thread {

		private final Socket socket;

		private ReportSaver(Socket socket) {
			this.socket = socket;
			setDaemon(true);
		}

		@Override
		public void run() {
			InputStream in = null;
			File reportFile = null;
			OutputStream out = null;
			try {
				socket.setSoTimeout(SOCKET_TIMEOUT_MS);
				in = socket.getInputStream();
				reportDir.mkdirs();
				reportFile = File.createTempFile(FILE_PREFIX, FILE_SUFFIX,
						reportDir);
				out = new FileOutputStream(reportFile);
				System.out.println("Saving report to " + reportFile);
				byte[] b = new byte[4096];
				int length = 0;
				while (true) {
					int read = in.read(b);
					if (read == -1) break;
					if (length + read > MAX_REPORT_LENGTH)
						throw new IOException("Report is too long");
					out.write(b, 0, read);
					length += read;
				}
				out.flush();
				System.out.println("Saved " + length + " bytes");
			} catch (IOException e) {
				e.printStackTrace();
				if (reportFile != null) reportFile.delete();
			} finally {
				tryToClose(in);
				tryToClose(out);
			}
		}
	}
}
