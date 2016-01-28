package org.briarproject.keyagreement;

import org.briarproject.api.FormatException;
import org.briarproject.api.TransportId;
import org.briarproject.api.data.BdfReader;
import org.briarproject.api.data.BdfReaderFactory;
import org.briarproject.api.keyagreement.Payload;
import org.briarproject.api.keyagreement.PayloadParser;
import org.briarproject.api.keyagreement.TransportDescriptor;
import org.briarproject.api.properties.TransportProperties;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import static org.briarproject.api.keyagreement.KeyAgreementConstants.COMMIT_LENGTH;
import static org.briarproject.api.keyagreement.KeyAgreementConstants.PROTOCOL_VERSION;
import static org.briarproject.api.properties.TransportPropertyConstants.MAX_PROPERTY_LENGTH;

class PayloadParserImpl implements PayloadParser {

	private final BdfReaderFactory bdfReaderFactory;

	@Inject
	public PayloadParserImpl(BdfReaderFactory bdfReaderFactory) {
		this.bdfReaderFactory = bdfReaderFactory;
	}

	@Override
	public Payload parse(byte[] raw) throws IOException {
		ByteArrayInputStream in = new ByteArrayInputStream(raw);
		BdfReader r = bdfReaderFactory.createReader(in);
		r.readListStart(); // Payload start
		int proto = (int) r.readLong();
		if (proto != PROTOCOL_VERSION)
			throw new FormatException();
		byte[] commitment = r.readRaw(COMMIT_LENGTH);
		if (commitment.length != COMMIT_LENGTH)
			throw new FormatException();
		List<TransportDescriptor> descriptors = new ArrayList<TransportDescriptor>();
		r.readListStart(); // Descriptors start
		while (r.hasList()) {
			r.readListStart();
			while (!r.hasListEnd()) {
				TransportId id =
						new TransportId(r.readString(MAX_PROPERTY_LENGTH));
				TransportProperties p = new TransportProperties();
				r.readDictionaryStart();
				while (!r.hasDictionaryEnd()) {
					String key = r.readString(MAX_PROPERTY_LENGTH);
					String value = r.readString(MAX_PROPERTY_LENGTH);
					p.put(key, value);
				}
				r.readDictionaryEnd();
				descriptors.add(new TransportDescriptor(id, p));
			}
			r.readListEnd();
		}
		r.readListEnd(); // Descriptors end
		r.readListEnd(); // Payload end
		if (!r.eof())
			throw new FormatException();
		return new Payload(commitment, descriptors);
	}
}
