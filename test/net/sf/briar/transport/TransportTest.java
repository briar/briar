package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import javax.crypto.Mac;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.crypto.CryptoModule;

import com.google.inject.Guice;
import com.google.inject.Injector;

public abstract class TransportTest extends BriarTestCase {

	protected final Mac mac;
	protected final ErasableKey macKey;
	protected final int macLength, maxPayloadLength;

	public TransportTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		mac = crypto.getMac();
		macKey = crypto.generateTestKey();
		macLength = mac.getMacLength();
		maxPayloadLength = MAX_FRAME_LENGTH - FRAME_HEADER_LENGTH - macLength;
	}
}
