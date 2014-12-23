package org.briarproject.crypto;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.briarproject.api.crypto.SecretKey;
import org.briarproject.util.ByteUtils;

class SecretKeyImpl implements SecretKey {

	private final byte[] key;

	private boolean erased = false; // Locking: this
	
	private final Lock synchLock = new ReentrantLock();

	SecretKeyImpl(byte[] key) {
		this.key = key;
	}

	public byte[] getEncoded() {
		synchLock.lock();
		try{
			if(erased) throw new IllegalStateException();
			return key;
		}
		finally{
			synchLock.unlock();
		}

	}

	public SecretKey copy() {
		return new SecretKeyImpl(key.clone());
	}

	public void erase() {
		synchLock.lock();
		try{
			if(erased) throw new IllegalStateException();
			ByteUtils.erase(key);
			erased = true;
		}
		finally{
			synchLock.unlock();
		}
	}
}
