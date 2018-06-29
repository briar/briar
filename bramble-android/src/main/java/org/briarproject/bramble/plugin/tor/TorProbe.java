package org.briarproject.bramble.plugin.tor;

import org.spongycastle.crypto.tls.Certificate;
import org.spongycastle.crypto.tls.CipherSuite;
import org.spongycastle.crypto.tls.DefaultTlsClient;
import org.spongycastle.crypto.tls.ServerOnlyTlsAuthentication;
import org.spongycastle.crypto.tls.TlsAuthentication;
import org.spongycastle.crypto.tls.TlsClientProtocol;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.INFO;

public class TorProbe {

	private static final Logger LOG =
			Logger.getLogger(TorProbe.class.getName());

	private static final int READ_TIMEOUT = 10 * 1000;

	// https://trac.torproject.org/projects/tor/wiki/org/projects/Tor/TLSHistory
	private static final int SSL3_RSA_FIPS_WITH_3DES_EDE_CBC_SHA = 0xfeff;

	// https://gitweb.torproject.org/torspec.git/tree/tor-spec.txt#n347
	private static final int[] TOR_CIPHER_SUITES = new int[] {
			CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
			CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
			CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA,
			CipherSuite.TLS_DHE_DSS_WITH_AES_256_CBC_SHA,
			CipherSuite.TLS_ECDH_RSA_WITH_AES_256_CBC_SHA,
			CipherSuite.TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA,
			CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
			CipherSuite.TLS_ECDHE_ECDSA_WITH_RC4_128_SHA,
			CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
			CipherSuite.TLS_ECDHE_RSA_WITH_RC4_128_SHA,
			CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
			CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA,
			CipherSuite.TLS_DHE_DSS_WITH_AES_128_CBC_SHA,
			CipherSuite.TLS_ECDH_RSA_WITH_RC4_128_SHA,
			CipherSuite.TLS_ECDH_RSA_WITH_AES_128_CBC_SHA,
			CipherSuite.TLS_ECDH_ECDSA_WITH_RC4_128_SHA,
			CipherSuite.TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA,
			CipherSuite.TLS_RSA_WITH_RC4_128_MD5,
			CipherSuite.TLS_RSA_WITH_RC4_128_SHA,
			CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
			CipherSuite.TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA,
			CipherSuite.TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA,
			CipherSuite.TLS_DHE_RSA_WITH_3DES_EDE_CBC_SHA,
			CipherSuite.TLS_DHE_DSS_WITH_3DES_EDE_CBC_SHA,
			CipherSuite.TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA,
			CipherSuite.TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA,
			SSL3_RSA_FIPS_WITH_3DES_EDE_CBC_SHA,
			CipherSuite.TLS_RSA_WITH_3DES_EDE_CBC_SHA
	};

	// https://gitweb.torproject.org/torspec.git/tree/tor-spec.txt#n412
	private static final byte[] VERSIONS_CELL = new byte[] {
			0x00, 0x00, // Circuit ID: 0
			0x07, // Command: Versions
			0x00, 0x06, // Payload length: 6 bytes
			0x00, 0x03, 0x00, 0x04, 0x00, 0x05 // Supported versions: 3, 4, 5
	};

	public List<Integer> probe(String address, int port) throws IOException {
		if (LOG.isLoggable(INFO))
			LOG.info("Connecting to " + address + ":" + port);
		Socket socket = new Socket(address, port);
		LOG.info("Connected");
		TlsClientProtocol client = new TlsClientProtocol(
				socket.getInputStream(), socket.getOutputStream(),
				new SecureRandom());
		client.connect(new TorTlsClient());
		LOG.info("TLS handshake succeeded");
		socket.setSoTimeout(READ_TIMEOUT);
		try {
			// Send a versions cell
			OutputStream out = client.getOutputStream();
			out.write(VERSIONS_CELL);
			out.flush();
			LOG.info("Sent versions cell");

			// Expect a versions cell in response
			List<Integer> versions = new ArrayList<>();
			DataInputStream in = new DataInputStream(client.getInputStream());
			int circuitId = in.readUnsignedShort();
			if (circuitId != 0)
				throw new IOException("Unexpected circuit ID: " + circuitId);
			int command = in.readUnsignedByte();
			if (command != 7)
				throw new IOException("Unexpected command: " + command);
			int payloadLength = in.readUnsignedShort();
			if (payloadLength == 0 || payloadLength % 2 != 0) {
				throw new IOException("Invalid payload length: "
						+ payloadLength);
			}
			for (int i = 0; i < payloadLength / 2; i++) {
				int version = in.readUnsignedShort();
				versions.add(version);
			}
			if (LOG.isLoggable(INFO))
				LOG.info("Supported versions: " + versions);
			return versions;
		} finally {
			client.close();
		}
	}

	public static void main(String[] args) throws IOException {
		if (args.length != 2) {
			System.err.println("Usage: TorProbe <address> <port>");
			System.exit(1);
		}
		String address = args[0];
		int port = Integer.parseInt(args[1]);
		new TorProbe().probe(address, port);
	}

	private static class TorTlsClient extends DefaultTlsClient {

		@Override
		public TlsAuthentication getAuthentication() {
			return new ServerOnlyTlsAuthentication() {
				@Override
				public void notifyServerCertificate(Certificate cert)
						throws IOException {
					LOG.info("Received server certificate");
					org.spongycastle.asn1.x509.Certificate[] chain =
							cert.getCertificateList();
					if (chain.length != 1)
						throw new IOException("Certificate is not self-signed");
					for (org.spongycastle.asn1.x509.Certificate c : chain) {
						if (LOG.isLoggable(INFO)) {
							LOG.info("Subject: " + c.getSubject());
							LOG.info("Issuer: " + c.getIssuer());
						}
					}
				}
			};
		}

		@Override
		public int[] getCipherSuites() {
			return TOR_CIPHER_SUITES;
		}
	}
}
