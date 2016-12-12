package org.briarproject.bramble.crypto;

import org.briarproject.bramble.BrambleTestCase;
import org.junit.Test;
import org.spongycastle.crypto.BlockCipher;
import org.spongycastle.crypto.engines.AESLightEngine;
import org.spongycastle.crypto.params.KeyParameter;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class FortunaGeneratorTest extends BrambleTestCase {

	@Test
	public void testCounterInitialisedToOne() {
		FortunaGenerator f = new FortunaGenerator(new byte[32]);
		// The counter is little-endian
		byte[] expected = new byte[16];
		expected[0] = 1;
		assertArrayEquals(expected, f.getCounter());
	}

	@Test
	public void testIncrementCounter() {
		FortunaGenerator f = new FortunaGenerator(new byte[32]);
		// Increment the counter until it reaches 255
		for (int i = 1; i < 255; i++) f.incrementCounter();
		byte[] expected = new byte[16];
		expected[0] = (byte) 255;
		assertArrayEquals(expected, f.getCounter());
		// Increment the counter again - it should carry into the next byte
		f.incrementCounter();
		expected[0] = 0;
		expected[1] = 1;
		assertArrayEquals(expected, f.getCounter());
		// Increment the counter until it carries into the next byte
		for (int i = 256; i < 65536; i++) f.incrementCounter();
		expected[0] = 0;
		expected[1] = 0;
		expected[2] = 1;
		assertArrayEquals(expected, f.getCounter());
	}

	@Test
	public void testNextBytes() {
		// Generate several outputs with the same seed - they should all match
		byte[] seed = new byte[32];
		byte[] out1 = new byte[48];
		new FortunaGenerator(seed).nextBytes(out1, 0, 48);
		// One byte longer than a block, with an offset of one
		byte[] out2 = new byte[49];
		new FortunaGenerator(seed).nextBytes(out2, 1, 48);
		for (int i = 0; i < 48; i++) assertEquals(out1[i], out2[i + 1]);
		// One byte shorter than a block
		byte[] out3 = new byte[47];
		new FortunaGenerator(seed).nextBytes(out3, 0, 47);
		for (int i = 0; i < 47; i++) assertEquals(out1[i], out3[i]);
		// Less than a block, with an offset greater than a block
		byte[] out4 = new byte[32];
		new FortunaGenerator(seed).nextBytes(out4, 17, 15);
		for (int i = 0; i < 15; i++) assertEquals(out1[i], out4[i + 17]);
	}

	@Test
	public void testRekeying() {
		byte[] seed = new byte[32];
		FortunaGenerator f = new FortunaGenerator(seed);
		// Generate three blocks of output
		byte[] out1 = new byte[48];
		f.nextBytes(out1, 0, 48);
		// Create another generator with the same seed and generate one block
		f = new FortunaGenerator(seed);
		byte[] out2 = new byte[16];
		f.nextBytes(out2, 0, 16);
		// The generator should have rekeyed with the 2nd and 3rd blocks
		byte[] expectedKey = new byte[32];
		System.arraycopy(out1, 16, expectedKey, 0, 32);
		// The generator's counter should have been incremented 3 times
		byte[] expectedCounter = new byte[16];
		expectedCounter[0] = 4;
		// The next expected output block is the counter encrypted with the key
		byte[] expectedOutput = new byte[16];
		BlockCipher c = new AESLightEngine();
		c.init(true, new KeyParameter(expectedKey));
		c.processBlock(expectedCounter, 0, expectedOutput, 0);
		// Check that the generator produces the expected output block
		byte[] out3 = new byte[16];
		f.nextBytes(out3, 0, 16);
		assertArrayEquals(expectedOutput, out3);
	}

	@Test
	public void testMaximumRequestLength() {
		int expectedMax = 1024 * 1024;
		byte[] output = new byte[expectedMax + 123];
		FortunaGenerator f = new FortunaGenerator(new byte[32]);
		assertEquals(expectedMax, f.nextBytes(output, 0, output.length));
	}
}
