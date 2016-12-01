package org.briarproject.bramble.api.reporting;

public interface ReportingConstants {

	/**
	 * Public key for reporting crashes and feedback to the developers. This
	 * is an ECIES key on the brainpoolp512r1 curve, encoded in SEC 1 format
	 * without point compression.
	 */
	String DEV_PUBLIC_KEY_HEX =
			"0457AD1619FBD433D5E13D5560697054"
					+ "6E8FC5F4EF83A8C18718E8BF59BB601F"
					+ "E20CCB233F06714A1BED370141A04C81"
					+ "808CF2EE95C7323CDEE5999670BD1174"
					+ "1F65ED691F355518E1A7E5E54BDDCA4C"
					+ "B86BD8DB8842BBFD706EBD9708DB8C04"
					+ "4FF006F215D83A66B3AEBAD674C4C1C4"
					+ "218121A38FA1FDD4A51E77588D90BD9652";

	/**
	 * Hidden service address for reporting crashes and feedback to the
	 * developers.
	 */
	String DEV_ONION_ADDRESS = "cwqmubyvnig3wag3.onion";
}
