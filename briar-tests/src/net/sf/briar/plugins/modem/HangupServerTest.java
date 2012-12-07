package net.sf.briar.plugins.modem;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.INFO;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class HangupServerTest {

	public static void main(String[] args) throws Exception {
		if(args.length != 1) {
			System.err.println("Please specify the serial port");
			System.exit(1);
		}
		String portName = args[0];
		Logger.getLogger("net.sf.briar").setLevel(INFO);
		ExecutorService executor = Executors.newCachedThreadPool();
		final CountDownLatch latch = new CountDownLatch(1);
		Modem.Callback callback = new Modem.Callback() {
			public void incomingCallConnected() {
				System.out.println("Connected");
				latch.countDown();
			}
		};
		try {
			final Modem modem = new ModemImpl(executor, callback, portName);
			modem.start();
			System.out.println("Waiting for incoming call");
			if(latch.await(60, SECONDS)) {
				System.out.println("Hanging up");
				modem.hangUp();
			} else {
				System.out.println("Did not connect");
			}
			modem.stop();
		} finally {
			executor.shutdown();
		}
	}
}
