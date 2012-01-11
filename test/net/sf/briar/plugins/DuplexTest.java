package net.sf.briar.plugins;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Scanner;

import net.sf.briar.api.ContactId;
import net.sf.briar.api.plugins.DuplexPlugin;
import net.sf.briar.api.plugins.DuplexTransportConnection;

abstract class DuplexTest {

	protected static final String CHALLENGE = "Carrots!";
	protected static final String RESPONSE = "Potatoes!";
	protected static final long INVITATION_TIMEOUT = 30 * 1000;

	protected final ContactId contactId = new ContactId(0);

	protected DuplexPlugin plugin = null;

	protected void sendChallengeReceiveResponse(DuplexTransportConnection d) {
		assert plugin != null;
		try {
			PrintStream out = new PrintStream(d.getOutputStream());
			out.println(CHALLENGE);
			System.out.println("Sent challenge: " + CHALLENGE);
			Scanner in = new Scanner(d.getInputStream());
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
			d.dispose(false, true);
		} catch(IOException e) {
			e.printStackTrace();
			d.dispose(true, true);
		}
	}

	protected void receiveChallengeSendResponse(DuplexTransportConnection d) {
		assert plugin != null;
		try {
			Scanner in = new Scanner(d.getInputStream());
			if(in.hasNextLine()) {
				String challenge = in.nextLine();
				System.out.println("Received challenge: " + challenge);
				if(CHALLENGE.equals(challenge)) {
					PrintStream out = new PrintStream(d.getOutputStream());
					out.println(RESPONSE);
					System.out.println("Sent response: " + RESPONSE);
				} else {
					System.out.println("Incorrect challenge");
				}
			} else {
				System.out.println("No challenge");
			}
			d.dispose(false, true);
		} catch(IOException e) {
			e.printStackTrace();
			d.dispose(true, true);
		}
	}
}
