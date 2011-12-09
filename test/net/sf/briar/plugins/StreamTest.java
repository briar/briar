package net.sf.briar.plugins;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Scanner;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.plugins.StreamPlugin;
import net.sf.briar.api.transport.StreamTransportConnection;

abstract class StreamTest {

	protected static final String CHALLENGE = "Carrots!";
	protected static final String RESPONSE = "Potatoes!";
	protected static final long INVITATION_TIMEOUT = 30 * 1000;

	protected final ContactId contactId = new ContactId(0);

	protected StreamPlugin plugin = null;

	protected void sendChallengeReceiveResponse(StreamTransportConnection s) {
		assert plugin != null;
		try {
			PrintStream out = new PrintStream(s.getOutputStream());
			out.println(CHALLENGE);
			System.out.println("Sent challenge: " + CHALLENGE);
			Scanner in = new Scanner(s.getInputStream());
			if(in.hasNextLine()) {
				String response = in.nextLine();
				System.out.println("Received response: " + response);
				if(RESPONSE.equals(response)) {
					System.out.println("Correct response");
				} else {
					System.out.println("Incorrect response");
				}
			} else {
				System.out.println("No response");
			}
			s.dispose(false, true);
		} catch(IOException e) {
			e.printStackTrace();
			s.dispose(true, true);
		}
	}

	protected void receiveChallengeSendResponse(StreamTransportConnection s) {
		assert plugin != null;
		try {
			Scanner in = new Scanner(s.getInputStream());
			if(in.hasNextLine()) {
				String challenge = in.nextLine();
				System.out.println("Received challenge: " + challenge);
				if(CHALLENGE.equals(challenge)) {
					PrintStream out = new PrintStream(s.getOutputStream());
					out.println(RESPONSE);
					System.out.println("Sent response: " + RESPONSE);
				} else {
					System.out.println("Incorrect challenge");
				}
			} else {
				System.out.println("No challenge");
			}
			s.dispose(false, true);
		} catch(IOException e) {
			e.printStackTrace();
			s.dispose(true, true);
		}
	}
}
