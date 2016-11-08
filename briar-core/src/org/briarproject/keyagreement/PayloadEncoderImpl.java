package org.briarproject.keyagreement;

import org.briarproject.api.TransportId;
import org.briarproject.api.data.BdfList;
import org.briarproject.api.data.BdfWriter;
import org.briarproject.api.data.BdfWriterFactory;
import org.briarproject.api.keyagreement.Payload;
import org.briarproject.api.keyagreement.PayloadEncoder;
import org.briarproject.api.nullsafety.NotNullByDefault;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.api.keyagreement.KeyAgreementConstants.PROTOCOL_VERSION;

@Immutable
@NotNullByDefault
class PayloadEncoderImpl implements PayloadEncoder {

	private final BdfWriterFactory bdfWriterFactory;

	@Inject
	PayloadEncoderImpl(BdfWriterFactory bdfWriterFactory) {
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
			Map<TransportId, BdfList> descriptors = p.getTransportDescriptors();
			for (BdfList descriptor : descriptors.values())
				w.writeList(descriptor);
			w.writeListEnd(); // Payload end
		} catch (IOException e) {
			// Shouldn't happen with ByteArrayOutputStream
			throw new AssertionError(e);
		}
		return out.toByteArray();
	}
}
