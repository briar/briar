package org.briarproject.bramble.plugin.tor;

interface TorRendezvousCrypto {

	String getOnionAddress(byte[] seed);

	String getPrivateKeyBlob(byte[] seed);
}
