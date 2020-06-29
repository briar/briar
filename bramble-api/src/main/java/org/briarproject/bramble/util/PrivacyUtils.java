package org.briarproject.bramble.util;

import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import javax.annotation.Nullable;

import static org.briarproject.bramble.util.StringUtils.isNullOrEmpty;
import static org.briarproject.bramble.util.StringUtils.isValidMac;
import static org.briarproject.bramble.util.StringUtils.toHexString;

@NotNullByDefault
public class PrivacyUtils {

	public static String scrubOnion(String onion) {
		// keep first three characters of onion address
		return onion.substring(0, 3) + "[scrubbed]";
	}

	@Nullable
	public static String scrubMacAddress(@Nullable String address) {
		if (isNullOrEmpty(address) || !isValidMac(address)) return address;
		// this is a fake address we need to know about
		if (address.equals("02:00:00:00:00:00")) return address;
		// keep first and last octet of MAC address
		return address.substring(0, 3) + "[scrubbed]"
				+ address.substring(14, 17);
	}

	public static String scrubInetAddress(InetAddress address) {
		if (address instanceof Inet4Address) {
			// Don't scrub local IPv4 addresses
			if (address.isLoopbackAddress() || address.isLinkLocalAddress() ||
					address.isSiteLocalAddress()) {
				return address.getHostAddress();
			}
			// Keep first and last octet of non-local IPv4 addresses
			return scrubIpv4Address(address.getAddress());
		} else {
			// Keep first and last octet of IPv6 addresses
			return scrubIpv6Address(address.getAddress());
		}
	}

	private static String scrubIpv4Address(byte[] ipv4) {
		return (ipv4[0] & 0xFF) + ".[scrubbed]." + (ipv4[3] & 0xFF);
	}

	private static String scrubIpv6Address(byte[] ipv6) {
		String hex = toHexString(ipv6).toLowerCase();
		return hex.substring(0, 2) + "[scrubbed]" + hex.substring(30);
	}

	public static String scrubSocketAddress(InetSocketAddress address) {
		return scrubInetAddress(address.getAddress());
	}

	public static String scrubSocketAddress(SocketAddress address) {
		if (address instanceof InetSocketAddress)
			return scrubSocketAddress((InetSocketAddress) address);
		return "[scrubbed]";
	}
}
