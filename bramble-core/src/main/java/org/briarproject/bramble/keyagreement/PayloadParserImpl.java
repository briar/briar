package org.briarproject.bramble.keyagreement;

import org.briarproject.bramble.api.FormatException;
import org.briarproject.bramble.api.UnsupportedVersionException;
import org.briarproject.bramble.api.data.BdfList;
import org.briarproject.bramble.api.data.BdfReader;
import org.briarproject.bramble.api.data.BdfReaderFactory;
import org.briarproject.bramble.api.keyagreement.Payload;
import org.briarproject.bramble.api.keyagreement.PayloadParser;
import org.briarproject.bramble.api.keyagreement.TransportDescriptor;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;
import org.briarproject.bramble.api.plugin.BluetoothConstants;
import org.briarproject.bramble.api.plugin.LanTcpConstants;
import org.briarproject.bramble.api.plugin.TransportId;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.concurrent.Immutable;
import javax.inject.Inject;

import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.BETA_PROTOCOL_VERSION;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.COMMIT_LENGTH;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.PROTOCOL_VERSION;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.TRANSPORT_ID_BLUETOOTH;
import static org.briarproject.bramble.api.keyagreement.KeyAgreementConstants.TRANSPORT_ID_LAN;

@Immutable
@NotNullByDefault
class PayloadParserImpl implements PayloadParser {

	private final BdfReaderFactory bdfReaderFactory;

	@Inject
	PayloadParserImpl(BdfReaderFactory bdfReaderFactory) {
		this.bdfReaderFactory = bdfReaderFactory;
	}

	@Override
	public Payload parse(byte[] raw) throws IOException {
		ByteArrayInputStream in = new ByteArrayInputStream(raw);
		// First byte: the protocol version
		int protocolVersion = in.read();
		if (protocolVersion == -1) throw new FormatException();
		if (protocolVersion != PROTOCOL_VERSION) {
			boolean tooOld = protocolVersion < PROTOCOL_VERSION ||
					protocolVersion == BETA_PROTOCOL_VERSION;
			throw new UnsupportedVersionException(tooOld);
		}
		// The rest of the payload is a BDF list with one or more elements
		BdfReader r = bdfReaderFactory.createReader(in);
		BdfList payload = r.readList();
		if (payload.isEmpty()) throw new FormatException();
		if (!r.eof()) throw new FormatException();
		// First element: the public key commitment
		byte[] commitment = payload.getRaw(0);
		if (commitment.length != COMMIT_LENGTH) throw new FormatException();
		// Remaining elements: transport descriptors
		List<TransportDescriptor> recognised = new ArrayList<>();
		for (int i = 1; i < payload.size(); i++) {
			BdfList descriptor = payload.getList(i);
			long transportId = descriptor.getLong(0);
			if (transportId == TRANSPORT_ID_BLUETOOTH) {
				TransportId id = BluetoothConstants.ID;
				recognised.add(new TransportDescriptor(id, descriptor));
			} else if (transportId == TRANSPORT_ID_LAN) {
				TransportId id = LanTcpConstants.ID;
				recognised.add(new TransportDescriptor(id, descriptor));
			}
		}
		return new Payload(commitment, recognised);
	}
}
