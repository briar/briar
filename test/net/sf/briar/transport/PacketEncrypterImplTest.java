package net.sf.briar.transport;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

import junit.framework.TestCase;
import net.sf.briar.api.crypto.CryptoComponent;
import net.sf.briar.crypto.CryptoModule;

import org.junit.Test;

import com.google.inject.Guice;
import com.google.inject.Injector;

public class PacketEncrypterImplTest extends TestCase {

	private final Cipher tagCipher, packetCipher;
	private final SecretKey tagKey, packetKey;

	public PacketEncrypterImplTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		tagCipher = crypto.getTagCipher();
		packetCipher = crypto.getPacketCipher();
		tagKey = crypto.generateSecretKey();
		packetKey = crypto.generateSecretKey();
	}

	@Test
	public void testSingleBytePacket() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PacketEncrypter p = new PacketEncrypterImpl(out, tagCipher,
				packetCipher, tagKey, packetKey);
		p.writeTag(new byte[Constants.TAG_BYTES]);
		p.getOutputStream().write((byte) 0);
		p.finishPacket();
		assertEquals(17, out.toByteArray().length);
	}

	@Test
	public void testEncryption() throws Exception {
		byte[] tag = new byte[Constants.TAG_BYTES];
		byte[] packet = new byte[123];
		// Calculate the expected encrypted tag
		tagCipher.init(Cipher.ENCRYPT_MODE, tagKey);
		byte[] expectedTag = tagCipher.doFinal(tag);
		assertEquals(tag.length, expectedTag.length);
		// Calculate the expected encrypted packet
		IvParameterSpec iv = new IvParameterSpec(tag);
		packetCipher.init(Cipher.ENCRYPT_MODE, packetKey, iv);
		byte[] expectedPacket = packetCipher.doFinal(packet);
		assertEquals(packet.length, expectedPacket.length);
		// Check that the PacketEncrypter gets the same results
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		PacketEncrypter p = new PacketEncrypterImpl(out, tagCipher,
				packetCipher, tagKey, packetKey);
		p.writeTag(tag);
		p.getOutputStream().write(packet);
		p.finishPacket();
		byte[] ciphertext = out.toByteArray();
		assertEquals(Constants.TAG_BYTES + packet.length, ciphertext.length);
		// Check the tag
		byte[] actualTag = new byte[Constants.TAG_BYTES];
		System.arraycopy(ciphertext, 0, actualTag, 0, Constants.TAG_BYTES);
		assertTrue(Arrays.equals(expectedTag, actualTag));
		// Check the packet
		byte[] actualPacket = new byte[packet.length];
		System.arraycopy(ciphertext, Constants.TAG_BYTES, actualPacket, 0,
				actualPacket.length);
		assertTrue(Arrays.equals(expectedPacket, actualPacket));
	}
}
