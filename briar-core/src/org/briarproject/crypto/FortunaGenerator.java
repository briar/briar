package org.briarproject.crypto;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.briarproject.api.crypto.MessageDigest;
import org.spongycastle.crypto.BlockCipher;
import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.crypto.engines.AESLightEngine;
import org.spongycastle.crypto.params.KeyParameter;

/**
 * Implements the Fortuna pseudo-random number generator, as described in
 * Ferguson and Schneier, <i>Practical Cryptography</i>, chapter 9.
 */
class FortunaGenerator {

	private static final int MAX_BYTES_PER_REQUEST = 1024 * 1024;
	private static final int KEY_BYTES = 32;
	private static final int BLOCK_BYTES = 16;

	private final Lock synchLock = new ReentrantLock();

	// The following are locking: synchLock
	private final MessageDigest digest = new DoubleDigest(new SHA256Digest());
	private final BlockCipher cipher = new AESLightEngine();
	private final byte[] key = new byte[KEY_BYTES];
	private final byte[] counter = new byte[BLOCK_BYTES];
	private final byte[] buffer = new byte[BLOCK_BYTES];
	private final byte[] newKey = new byte[KEY_BYTES];

	FortunaGenerator(byte[] seed) {
		reseed(seed);
	}

	void reseed(byte[] seed) {
		synchLock.lock();
		try {
			digest.update(key);
			digest.update(seed);
			digest.digest(key, 0, KEY_BYTES);
			incrementCounter();
		} finally {
			synchLock.unlock();
		}

	}

	// Package access for testing
	void incrementCounter() {
		synchLock.lock();
		try {
			counter[0]++;
			for(int i = 0; counter[i] == 0; i++) {
				if(i + 1 == BLOCK_BYTES)
					throw new RuntimeException("Counter exhausted");
				counter[i + 1]++;
			}
		} finally {
			synchLock.unlock();
		}
	}

	// Package access for testing
	byte[] getCounter() {
		synchLock.lock();
		try {
			return counter;
		} finally {
			synchLock.unlock();
		}

	}

	int nextBytes(byte[] dest, int off, int len) {
		synchLock.lock();
		try {
			// Don't write more than the maximum number of bytes in one request
			if(len > MAX_BYTES_PER_REQUEST) len = MAX_BYTES_PER_REQUEST;
			cipher.init(true, new KeyParameter(key));
			// Generate full blocks directly into the output buffer
			int fullBlocks = len / BLOCK_BYTES;
			for(int i = 0; i < fullBlocks; i++) {
				cipher.processBlock(counter, 0, dest, off + i * BLOCK_BYTES);
				incrementCounter();
			}
			// Generate a partial block if needed
			int done = fullBlocks * BLOCK_BYTES, remaining = len - done;
			assert remaining < BLOCK_BYTES;
			if(remaining > 0) {
				cipher.processBlock(counter, 0, buffer, 0);
				incrementCounter();
				// Copy the partial block to the output buffer and erase our copy
				System.arraycopy(buffer, 0, dest, off + done, remaining);
				for(int i = 0; i < BLOCK_BYTES; i++) buffer[i] = 0;
			}
			// Generate a new key
			for(int i = 0; i < KEY_BYTES / BLOCK_BYTES; i++) {
				cipher.processBlock(counter, 0, newKey, i * BLOCK_BYTES);
				incrementCounter();
			}
			System.arraycopy(newKey, 0, key, 0, KEY_BYTES);
			for(int i = 0; i < KEY_BYTES; i++) newKey[i] = 0;
			// Return the number of bytes written
			return len;
		} finally {
			synchLock.unlock();
		}
	}
}
