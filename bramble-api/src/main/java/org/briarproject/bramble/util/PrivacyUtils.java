package org.briarproject.bramble.util;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import javax.annotation.Nullable;

@NotNullByDefault
public class PrivacyUtils {

	public static String scrubOnion(String onion) {
		// keep first three characters of onion address
		return onion.substring(0, 3) + "[scrubbed]";
	}

	@Nullable
	public static String scrubMacAddress(@Nullable String address) {
		if (address == null || address.length() == 0) return null;
		// this is a fake address we need to know about
		if (address.equals("02:00:00:00:00:00")) return address;
		// keep first and last octet of MAC address
		return address.substring(0, 3) + "[scrubbed]"
				+ address.substring(14, 17);
	}

	@Nullable
	public static String scrubInetAddress(InetAddress address) {
		// don't scrub link and site local addresses
		if (address.isLinkLocalAddress() || address.isSiteLocalAddress())
			return address.toString();
		// completely scrub IPv6 addresses
		if (address instanceof Inet6Address) return "[scrubbed]";
		// keep first and last octet of IPv4 addresses
		return scrubInetAddress(address.toString());
	}

	@Nullable
	public static String scrubInetAddress(@Nullable String address) {
		if (address == null) return null;

		int firstDot = address.indexOf(".");
		if (firstDot == -1) return "[scrubbed]";
		String prefix = address.substring(0, firstDot + 1);
		int lastDot = address.lastIndexOf(".");
		String suffix = address.substring(lastDot, address.length());
		return prefix + "[scrubbed]" + suffix;
	}

	@Nullable
	public static String scrubSocketAddress(InetSocketAddress address) {
		InetAddress inetAddress = address.getAddress();
		return scrubInetAddress(inetAddress);
	}

	@Nullable
	public static String scrubSocketAddress(SocketAddress address) {
		if (address instanceof InetSocketAddress)
			return scrubSocketAddress((InetSocketAddress) address);
		return scrubInetAddress(address.toString());
	}
}
