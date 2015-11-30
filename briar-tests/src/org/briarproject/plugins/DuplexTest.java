package org.briarproject.plugins;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Random;
import java.util.Scanner;

import org.briarproject.api.ContactId;
import org.briarproject.api.crypto.PseudoRandom;
import org.briarproject.api.plugins.TransportConnectionReader;
import org.briarproject.api.plugins.TransportConnectionWriter;
import org.briarproject.api.plugins.duplex.DuplexPlugin;
import org.briarproject.api.plugins.duplex.DuplexTransportConnection;

abstract class DuplexTest {

	protected static final String CHALLENGE = "Carrots!";
	protected static final String RESPONSE = "Potatoes!";

	protected final ContactId contactId = new ContactId(234);

	protected DuplexPlugin plugin = null;

	protected void sendChallengeReceiveResponse(DuplexTransportConnection d) {
		assert plugin != null;
		TransportConnectionReader r = d.getReader();
		TransportConnectionWriter w = d.getWriter();
		try {
			PrintStream out = new PrintStream(w.getOutputStream());
			out.println(CHALLENGE);
			out.flush();
			System.out.println("Sent challenge: " + CHALLENGE);
			Scanner in = new Scanner(r.getInputStream());
			if (in.hasNextLine()) {
				String response = in.nextLine();
				System.out.println("Received response: " + response);
				if (RESPONSE.equals(response)) {
					System.out.println("Correct response");
				} else {
					System.out.println("Incorrect response");
				}
			} else {
				System.out.println("No response");
			}
			r.dispose(false, true);
			w.dispose(false);
		} catch (IOException e) {
			e.printStackTrace();
			try {
				r.dispose(true, true);
				w.dispose(true);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

	protected void receiveChallengeSendResponse(DuplexTransportConnection d) {
		assert plugin != null;
		TransportConnectionReader r = d.getReader();
		TransportConnectionWriter w = d.getWriter();
		try {
			Scanner in = new Scanner(r.getInputStream());
			if (in.hasNextLine()) {
				String challenge = in.nextLine();
				System.out.println("Received challenge: " + challenge);
				if (CHALLENGE.equals(challenge)) {

					PrintStream out = new PrintStream(w.getOutputStream());
					out.println(RESPONSE);
					out.flush();
					System.out.println("Sent response: " + RESPONSE);
				} else {
					System.out.println("Incorrect challenge");
				}
			} else {
				System.out.println("No challenge");
			}
			r.dispose(false, true);
			w.dispose(false);
		} catch (IOException e) {
			e.printStackTrace();
			try {
				r.dispose(true, true);
				w.dispose(true);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
	}

	protected PseudoRandom getPseudoRandom(int seed) {
		final Random random = new Random(seed);
		return new PseudoRandom() {
			public byte[] nextBytes(int bytes) {
				byte[] b = new byte[bytes];
				random.nextBytes(b);
				return b;
			}
		};
	}
}
