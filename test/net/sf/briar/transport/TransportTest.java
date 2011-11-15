package net.sf.briar.transport;

import static net.sf.briar.api.transport.TransportConstants.MAX_FRAME_LENGTH;

import javax.crypto.Mac;
import net.sf.briar.api.crypto.ErasableKey;

import junit.framework.TestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.crypto.CryptoModule;
import net.sf.briar.util.ByteUtils;

import com.google.inject.Guice;
import com.google.inject.Injector;

public abstract class TransportTest extends TestCase {

	protected final Mac mac;
	protected final ErasableKey macKey;
	protected final int headerLength = 4, macLength, maxPayloadLength;

	public TransportTest() throws Exception {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		mac = crypto.getMac();
		macKey = crypto.generateTestKey();
		macLength = mac.getMacLength();
		maxPayloadLength = MAX_FRAME_LENGTH - headerLength - macLength;
	}

	static void writeHeader(byte[] b, int payload, int padding) {
		ByteUtils.writeUint16(payload, b, 0);
		ByteUtils.writeUint16(padding, b, 2);
	}
}
