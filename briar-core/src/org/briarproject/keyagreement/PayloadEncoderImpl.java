package org.briarproject.keyagreement;

import org.briarproject.api.data.BdfWriter;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.keyagreement.Payload;
import org.briarproject.api.keyagreement.PayloadEncoder;
import org.briarproject.api.keyagreement.TransportDescriptor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.inject.Inject;

import static org.briarproject.api.keyagreement.KeyAgreementConstants.PROTOCOL_VERSION;

class PayloadEncoderImpl implements PayloadEncoder {

	private final BdfWriterFactory bdfWriterFactory;

	@Inject
	public PayloadEncoderImpl(BdfWriterFactory bdfWriterFactory) {
		this.bdfWriterFactory = bdfWriterFactory;
	}

	@Override
	public byte[] encode(Payload p) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		BdfWriter w = bdfWriterFactory.createWriter(out);
		try {
			w.writeListStart(); // Payload start
			w.writeLong(PROTOCOL_VERSION);
			w.writeRaw(p.getCommitment());
			w.writeListStart(); // Descriptors start
			for (TransportDescriptor d : p.getTransportDescriptors()) {
				w.writeListStart();
				w.writeString(d.getIdentifier().getString());
				w.writeDictionary(d.getProperties());
				w.writeListEnd();
			}
			w.writeListEnd(); // Descriptors end
			w.writeListEnd(); // Payload end
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayOutputStream
			throw new RuntimeException(e);
		}
		return out.toByteArray();
	}
}
