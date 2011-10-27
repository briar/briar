package net.sf.briar.plugins.bluetooth;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Scanner;

import net.sf.briar.api.transport.StreamTransportConnection;

abstract class BluetoothTest {

	protected static final String UUID = "CABBA6E5CABBA6E5CABBA6E5CABBA6E5";
	protected static final String CHALLENGE = "Carrots!";
	protected static final String RESPONSE = "Potatoes!";
	protected static final long INVITATION_TIMEOUT = 30 * 1000;

	void sendChallengeAndReceiveResponse(StreamTransportConnection s) {
		try {
			PrintStream out = new PrintStream(s.getOutputStream());
			out.println(CHALLENGE);
			System.out.println("Sent challenge: " + CHALLENGE);
			Scanner in = new Scanner(s.getInputStream());
			String response = in.nextLine();
			System.out.println("Received response: " + response);
			if(BluetoothClientTest.RESPONSE.equals(response)) {
				System.out.println("Correct response");
			} else {
				System.out.println("Incorrect response");
			}
			s.dispose(true);
		} catch(IOException e) {
			e.printStackTrace();
			s.dispose(false);
		}
	}

	void receiveChallengeAndSendResponse(StreamTransportConnection s) {
		try {
			Scanner in = new Scanner(s.getInputStream());
			String challenge = in.nextLine();
			System.out.println("Received challenge: " + challenge);
			if(BluetoothServerTest.CHALLENGE.equals(challenge)) {
				PrintStream out = new PrintStream(s.getOutputStream());
				out.println(RESPONSE);
				System.out.println("Sent response: " + RESPONSE);
			} else {
				System.out.println("Incorrect challenge");
			}
			s.dispose(true);
		} catch(IOException e) {
			e.printStackTrace();
			s.dispose(false);
		}
	}
}