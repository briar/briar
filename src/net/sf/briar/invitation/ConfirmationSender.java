package net.sf.briar.invitation;

import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;

import net.sf.briar.api.invitation.ConfirmationCallback;

class ConfirmationSender implements ConfirmationCallback {

	private static final Logger LOG =
			Logger.getLogger(ConfirmationSender.class.getName());

	private final OutputStream out;

	ConfirmationSender(OutputStream out) {
		this.out = out;
	}

	public void codesMatch() {
		write(1);
	}

	public void codesDoNotMatch() {
		write(0);
	}

	private void write(int b) {
		try {
			out.write(b);
			out.flush();
		} catch(IOException e) {
			if(LOG.isLoggable(WARNING)) LOG.warning(e.toString());
		}
	}
}
