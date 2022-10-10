package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.api.WeakSingletonProvider;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import javax.annotation.Nonnull;
import javax.net.SocketFactory;

import okhttp3.OkHttpClient;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.briarproject.bramble.test.TestUtils.getRandomId;

class MailboxTestUtils {

	static String getQrCodePayload(byte[] onionBytes, byte[] setupToken) {
		byte[] payloadBytes = ByteBuffer.allocate(65)
				.put((byte) 32) // 1
				.put(onionBytes) // 32
				.put(setupToken) // 32
				.array();
		//noinspection CharsetObjectCanBeUsed
		return new String(payloadBytes, Charset.forName("ISO-8859-1"));
	}

	static String getQrCodePayload(byte[] setupToken) {
		return getQrCodePayload(getRandomId(), setupToken);
	}

	static WeakSingletonProvider<OkHttpClient> createHttpClientProvider() {
		return new WeakSingletonProvider<OkHttpClient>() {
			@Override
			@Nonnull
			public OkHttpClient createInstance() {
				return new OkHttpClient.Builder()
						.socketFactory(SocketFactory.getDefault())
						.connectTimeout(60_000, MILLISECONDS)
						.build();
			}
		};
	}
}
