package org.briarproject.bramble.mailbox;

import org.briarproject.bramble.BrambleCoreIntegrationTestEagerSingletons;
import org.briarproject.bramble.api.WeakSingletonProvider;
import org.briarproject.bramble.api.mailbox.MailboxAuthToken;
import org.briarproject.bramble.test.TestDatabaseConfigModule;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;
import javax.net.SocketFactory;

import dagger.Module;
import dagger.Provides;
import okhttp3.OkHttpClient;

import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.Assert.fail;

class MailboxIntegrationTestUtils {

	static final String URL_BASE = "http://127.0.0.1:8000";

	static String getQrCodePayload(MailboxAuthToken setupToken) {
		byte[] bytes = getQrCodeBytes(setupToken);
		Charset charset = Charset.forName("ISO-8859-1");
		return new String(bytes, charset);
	}

	private static byte[] getQrCodeBytes(MailboxAuthToken setupToken) {
		byte[] hiddenServiceBytes = getHiddenServiceBytes();
		byte[] setupTokenBytes = setupToken.getBytes();
		return ByteBuffer.allocate(65).put((byte) 32)
				.put(hiddenServiceBytes).put(setupTokenBytes).array();
	}

	private static byte[] getHiddenServiceBytes() {
		byte[] data = new byte[32];
		Arrays.fill(data, (byte) 'a');
		return data;
	}

	private static WeakSingletonProvider<OkHttpClient> createHttpClientProvider() {
		OkHttpClient client = new OkHttpClient.Builder()
				.socketFactory(SocketFactory.getDefault())
				.connectTimeout(60_000, MILLISECONDS)
				.build();
		return new WeakSingletonProvider<OkHttpClient>() {
			@Override
			@Nonnull
			public OkHttpClient createInstance() {
				return client;
			}
		};
	}

	static MailboxApi createMailboxApi() {
		return new MailboxApiImpl(createHttpClientProvider(),
				new TestUrlConverter());
	}

	static MailboxIntegrationTestComponent createTestComponent(
			File databaseDir) {
		MailboxIntegrationTestComponent component =
				DaggerMailboxIntegrationTestComponent
						.builder()
						.testDatabaseConfigModule(
								new TestDatabaseConfigModule(databaseDir))
						.build();
		BrambleCoreIntegrationTestEagerSingletons.Helper
				.injectEagerSingletons(component);
		return component;
	}

	@Module
	static class TestUrlConverterModule {

		@Provides
		UrlConverter provideUrlConverter() {
			return new TestUrlConverter();
		}
	}

	interface Check {
		boolean check() throws Exception;
	}

	/**
	 * Run the specified method {@code check} every {@code step} milliseconds
	 * until either {@code check} returns true or longer than {@code totalTime}
	 * milliseconds have been spent checking the function and waiting for the
	 * next invocation.
	 */
	static void retryUntilSuccessOrTimeout(long totalTime, long step,
			Check check) throws Exception {
		AtomicBoolean success = new AtomicBoolean(false);

		checkRepeatedly(totalTime, step, () -> {
					boolean result = check.check();
					if (result) success.set(true);
					return result;
				}
		);

		if (!success.get()) {
			fail("timeout reached");
		}
	}

	/**
	 * Run the specified method {@code check} every {@code step} milliseconds
	 * until either {@code check} returns true or longer than {@code totalTime}
	 * milliseconds have been spent checking the function and waiting for the
	 * next invocation.
	 */
	private static void checkRepeatedly(long totalTime, long step,
			Check check) throws Exception {
		long start = currentTimeMillis();
		while (currentTimeMillis() - start < totalTime) {
			if (check.check()) {
				return;
			}
			try {
				Thread.sleep(step);
			} catch (InterruptedException ignore) {
				// continue
			}
		}
	}
}
