package org.briarproject.properties;

import org.briarproject.api.FormatException;
import org.briarproject.api.clients.BdfMessageContext;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.nullsafety.NotNullByDefault;
import org.briarproject.api.sync.Group;
import org.briarproject.api.sync.Message;
import org.briarproject.api.system.Clock;
import org.briarproject.clients.BdfMessageValidator;

import static org.briarproject.api.TransportId.MAX_TRANSPORT_ID_LENGTH;
import static org.briarproject.api.properties.TransportPropertyConstants.MAX_PROPERTIES_PER_TRANSPORT;
import static org.briarproject.api.properties.TransportPropertyConstants.MAX_PROPERTY_LENGTH;

@NotNullByDefault
public class TransportPropertyValidator extends BdfMessageValidator {

	TransportPropertyValidator(ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {
		super(clientHelper, metadataEncoder, clock);
	}

	@Override
	protected BdfMessageContext validateMessage(Message m, Group g,
			BdfList body) throws FormatException {
		// Transport ID, version, properties
		checkSize(body, 3);
		// Transport ID
		String transportId = body.getString(0);
		checkLength(transportId, 1, MAX_TRANSPORT_ID_LENGTH);
		// Version
		long version = body.getLong(1);
		if (version < 0) throw new FormatException();
		// Properties
		BdfDictionary dictionary = body.getDictionary(2);
		checkSize(dictionary, 0, MAX_PROPERTIES_PER_TRANSPORT);
		for (String key : dictionary.keySet()) {
			checkLength(key, 0, MAX_PROPERTY_LENGTH);
			String value = dictionary.getString(key);
			checkLength(value, 0, MAX_PROPERTY_LENGTH);
		}
		// Return the metadata
		BdfDictionary meta = new BdfDictionary();
		meta.put("transportId", transportId);
		meta.put("version", version);
		meta.put("local", false);
		return new BdfMessageContext(meta);
	}
}
