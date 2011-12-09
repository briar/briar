package net.sf.briar.protocol;

import static org.junit.Assert.assertArrayEquals;

import java.util.Random;

import net.sf.briar.BriarTestCase;
import net.sf.briar.api.FormatException;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.api.crypto.MessageDigest;
import net.sf.briar.api.serial.CopyingConsumer;
import net.sf.briar.api.serial.CountingConsumer;
import net.sf.briar.api.serial.DigestingConsumer;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Before;
import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class ConsumersTest extends BriarTestCase {

	private CryptoComponent crypto = null;

	@Before
	public void setUp() {
		Injector i = Guice.createInjector(new CryptoModule());
		crypto = i.getInstance(CryptoComponent.class);
	}

	@Test
	public void testDigestingConsumer() throws Exception {
		byte[] data = new byte[1234];
		// Generate some random data and digest it
		new Random().nextBytes(data);
		MessageDigest messageDigest = crypto.getMessageDigest();
		messageDigest.update(data);
		byte[] dig = messageDigest.digest();
		// Check that feeding a DigestingConsumer generates the same digest
		DigestingConsumer dc = new DigestingConsumer(messageDigest);
		dc.write(data[0]);
		dc.write(data, 1, data.length - 2);
		dc.write(data[data.length - 1]);
		byte[] dig1 = messageDigest.digest();
		assertArrayEquals(dig, dig1);
	}

	@Test
	public void testCountingConsumer() throws Exception {
		byte[] data = new byte[1234];
		CountingConsumer cc = new CountingConsumer(data.length);
		cc.write(data[0]);
		cc.write(data, 1, data.length - 2);
		cc.write(data[data.length - 1]);
		assertEquals(data.length, cc.getCount());
		try {
			cc.write((byte) 0);
			fail();
		} catch(FormatException expected) {}
	}

	@Test
	public void testCopyingConsumer() throws Exception {
		byte[] data = new byte[1234];
		new Random().nextBytes(data);
		// Check that a CopyingConsumer creates a faithful copy
		CopyingConsumer cc = new CopyingConsumer();
		cc.write(data[0]);
		cc.write(data, 1, data.length - 2);
		cc.write(data[data.length - 1]);
		assertArrayEquals(data, cc.getCopy());
	}
}
