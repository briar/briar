package net.sf.briar.transport;

import java.io.ByteArrayInputStream;
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

public class PacketDecrypterImplTest extends TestCase {

	private final Cipher tagCipher, packetCipher;
	private final SecretKey tagKey, packetKey;

	public PacketDecrypterImplTest() {
		super();
		Injector i = Guice.createInjector(new CryptoModule());
		CryptoComponent crypto = i.getInstance(CryptoComponent.class);
		tagCipher = crypto.getTagCipher();
		packetCipher = crypto.getPacketCipher();
		tagKey = crypto.generateSecretKey();
		packetKey = crypto.generateSecretKey();
	}

	@Test
	public void testSingleBytePackets() throws Exception {
		byte[] ciphertext = new byte[(Constants.TAG_BYTES + 1) * 2];
		ByteArrayInputStream in = new ByteArrayInputStream(ciphertext);
		byte[] firstTag = new byte[Constants.TAG_BYTES];
		assertEquals(Constants.TAG_BYTES, in.read(firstTag));
		PacketDecrypter p = new PacketDecrypterImpl(firstTag, in, tagCipher,
				packetCipher, tagKey, packetKey);
		byte[] decryptedTag = p.readTag();
		assertEquals(Constants.TAG_BYTES, decryptedTag.length);
		assertTrue(p.getInputStream().read() > -1);
		byte[] decryptedTag1 = p.readTag();
		assertEquals(Constants.TAG_BYTES, decryptedTag1.length);
		assertTrue(p.getInputStream().read() > -1);		
	}

	@Test
	public void testDecryption() throws Exception {
		byte[] tag = new byte[Constants.TAG_BYTES];
		byte[] packet = new byte[123];
		byte[] tag1 = new byte[Constants.TAG_BYTES];
		byte[] packet1 = new byte[234];
		// Calculate the first expected decrypted tag
		tagCipher.init(Cipher.DECRYPT_MODE, tagKey);
		byte[] expectedTag = tagCipher.doFinal(tag);
		assertEquals(tag.length, expectedTag.length);
		// Calculate the first expected decrypted packet
		IvParameterSpec iv = new IvParameterSpec(expectedTag);
		packetCipher.init(Cipher.DECRYPT_MODE, packetKey, iv);
		byte[] expectedPacket = packetCipher.doFinal(packet);
		assertEquals(packet.length, expectedPacket.length);
		// Calculate the second expected decrypted tag
		tagCipher.init(Cipher.DECRYPT_MODE, tagKey);
		byte[] expectedTag1 = tagCipher.doFinal(tag1);
		assertEquals(tag1.length, expectedTag1.length);
		// Calculate the second expected decrypted packet
		IvParameterSpec iv1 = new IvParameterSpec(expectedTag1);
		packetCipher.init(Cipher.DECRYPT_MODE, packetKey, iv1);
		byte[] expectedPacket1 = packetCipher.doFinal(packet1);
		assertEquals(packet1.length, expectedPacket1.length);
		// Check that the PacketDecrypter gets the same results
		byte[] ciphertext = new byte[tag.length + packet.length
		                             + tag1.length + packet1.length];
		System.arraycopy(tag, 0, ciphertext, 0, tag.length);
		System.arraycopy(packet, 0, ciphertext, tag.length, packet.length);
		System.arraycopy(tag1, 0, ciphertext, tag.length + packet.length,
				tag1.length);
		System.arraycopy(packet1, 0, ciphertext,
				tag.length + packet.length + tag1.length, packet1.length);
		ByteArrayInputStream in = new ByteArrayInputStream(ciphertext);
		PacketDecrypter p = new PacketDecrypterImpl(tag, in, tagCipher,
				packetCipher, tagKey, packetKey);
		// First tag
		assertTrue(Arrays.equals(expectedTag, p.readTag()));
		// First packet
		byte[] actualPacket = new byte[packet.length];
		int offset = 0;
		while(offset < actualPacket.length) {
			int read = p.getInputStream().read(actualPacket, offset,
					actualPacket.length - offset);
			if(read == -1) break;
			offset += read;
		}
		assertEquals(actualPacket.length, offset);
		assertTrue(Arrays.equals(expectedPacket, actualPacket));
		// Second tag
		assertTrue(Arrays.equals(expectedTag1, p.readTag()));
		// Second packet
		byte[] actualPacket1 = new byte[packet1.length];
		offset = 0;
		while(offset < actualPacket1.length) {
			int read = p.getInputStream().read(actualPacket1, offset,
					actualPacket1.length - offset);
			if(read == -1) break;
			offset += read;
		}
		assertEquals(actualPacket1.length, offset);
		assertTrue(Arrays.equals(expectedPacket1, actualPacket1));
	}
}
