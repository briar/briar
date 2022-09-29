package org.briarproject.bramble.io;

import org.briarproject.nullsafety.NotNullByDefault;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import okhttp3.Dns;

@NotNullByDefault
class NoDns implements Dns {

	private static final byte[] UNSPECIFIED_ADDRESS = new byte[4];

	@Inject
	public NoDns() {
	}

	@Override
	public List<InetAddress> lookup(String hostname)
			throws UnknownHostException {
		InetAddress unspecified =
				InetAddress.getByAddress(hostname, UNSPECIFIED_ADDRESS);
		return Collections.singletonList(unspecified);
	}

}
