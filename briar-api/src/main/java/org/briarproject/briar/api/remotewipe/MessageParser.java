package org.briarproject.briar.api.remotewipe;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.data.BdfList;

public interface MessageParser {
	void parseSetupMessage(BdfList body) throws FormatException;

	void parseWipeMessage(BdfList body) throws FormatException;

	void parseRevokeMessage(BdfList body) throws FormatException;
}
