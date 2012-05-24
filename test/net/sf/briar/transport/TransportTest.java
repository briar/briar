package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.FRAME_HEADER_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAC_LENGTH;
import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import javax.crypto.Cipher;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.ErasableKey;
import net.sf.briar.crypto.CryptoModule;

import com.google.inject.Guice;
import com.google.inject.Injector;

public abstract class TransportTest extends BriarTestCase {

	static final int MAX_PAYLOAD_LENGTH =
		MAX_FRAME_LENGTH - FRAME_HEADER_LENGTH - MAC_LENGTH;

	protected final Cipher frameCipher;
	protected final ErasableKey frameKey;

	public TransportTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		frameCipher = crypto.getFrameCipher();
		frameKey = crypto.generateTestKey();
	}
}
