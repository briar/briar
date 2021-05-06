package org.briarproject.briar.remotewipe;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.briar.api.remotewipe.MessageParser;

import javax.inject.Inject;

public class MessageParserImpl implements MessageParser {

	@Inject
	MessageParserImpl () {
	}

	@Override
	public void parseSetupMessage(BdfList body) throws FormatException {

	}

	@Override
	public void parseWipeMessage(BdfList body) throws FormatException {

	}
}
