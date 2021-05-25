package org.briarproject.briar.remotewipe;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.client.ClientHelper;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.briar.api.remotewipe.MessageEncoder;

import javax.inject.Inject;

import static org.briarproject.briar.api.remotewipe.MessageType.SETUP;
import static org.briarproject.briar.api.remotewipe.MessageType.WIPE;

public class MessageEncoderImpl implements MessageEncoder {

	private final ClientHelper clientHelper;

	@Inject
	MessageEncoderImpl(ClientHelper clientHelper) {
		this.clientHelper = clientHelper;
	}

	@Override
	public byte[] encodeSetupMessage() {
		BdfList body = BdfList.of(
				SETUP.getValue()
		);
		return encodeBody(body);
	}

	@Override
	public byte[] encodeWipeMessage() {
		BdfList body = BdfList.of(
				WIPE.getValue()
		);
		return encodeBody(body);
	}

	private byte[] encodeBody(BdfList body) {
		try {
			return clientHelper.toByteArray(body);
		} catch (FormatException e) {
			throw new AssertionError(e);
		}
	}
}
