package org.briarproject.properties;

import org.briarproject.api.FormatException;
import org.briarproject.api.UniqueId;
import org.briarproject.api.clients.ClientHelper;
import org.briarproject.api.data.BdfDictionary;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.MetadataEncoder;
import org.briarproject.api.sync.Group;
import org.briarproject.api.system.Clock;
import org.briarproject.clients.BdfMessageValidator;

import static org.briarproject.api.TransportId.MAX_TRANSPORT_ID_LENGTH;
import static org.briarproject.api.properties.TransportPropertyConstants.MAX_PROPERTIES_PER_TRANSPORT;
import static org.briarproject.api.properties.TransportPropertyConstants.MAX_PROPERTY_LENGTH;

class TransportPropertyValidator extends BdfMessageValidator {

	TransportPropertyValidator(ClientHelper clientHelper,
			MetadataEncoder metadataEncoder, Clock clock) {
		super(clientHelper, metadataEncoder, clock);
	}

	@Override
	protected BdfDictionary validateMessage(BdfList message, Group g,
			long timestamp) throws FormatException {
		// Device ID, transport ID, version, properties
		checkSize(message, 4);
		// Device ID
		byte[] deviceId = message.getRaw(0);
		checkLength(deviceId, UniqueId.LENGTH);
		// Transport ID
		String transportId = message.getString(1);
		checkLength(transportId, 1, MAX_TRANSPORT_ID_LENGTH);
		// Version
		long version = message.getLong(2);
		if (version < 0) throw new FormatException();
		// Properties
		BdfDictionary dictionary = message.getDictionary(3);
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
		return meta;
	}
}
