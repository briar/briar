package net.sf.briar.plugins.modem;

import static java.util.logging.Level.INFO;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class HangupClientTest {

	public static void main(String[] args) throws Exception {
		if(args.length != 2) {
			System.err.println("Please specify the server's phone number "
					+ " and the serial port");
			System.exit(1);
		}
		String number = args[0];
		String portName = args[1];
		Logger.getLogger("net.sf.briar").setLevel(INFO);
		ExecutorService executor = Executors.newCachedThreadPool();
		Modem.Callback callback = new Modem.Callback() {
			public void incomingCallConnected() {
				System.err.println("Unexpected incoming call");
				System.exit(1);
			}
		};
		try {
			Modem modem = new ModemImpl(executor, callback, portName);
			modem.start();
			System.out.println("Dialling");
			if(modem.dial(number)) {
				System.out.println("Connected");
				Thread.sleep(10 * 1000);
			} else {
				System.out.println("Did not connect");
			}
			modem.stop();
		} finally {
			executor.shutdown();
		}
	}
}
