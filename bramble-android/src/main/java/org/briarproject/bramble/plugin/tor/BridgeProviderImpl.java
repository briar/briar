package org.briarproject.bramble.plugin.tor;

import java.util.Arrays;
import java.util.List;

public class BridgeProviderImpl implements BridgeProvider {

	final static String[] BRIDGES = {
		"Bridge 131.252.210.150:8081 0E858AC201BF0F3FA3C462F64844CBFFC7297A42",
//		"Bridge 128.105.214.161:8081 1E326AAFB3FCB515015250D8FCCC8E37F91A153B",
		"Bridge 67.205.189.122:8443 12D64D5D44E20169585E7378580C0D33A872AD98",
		"Bridge 45.32.148.146:8443 0CE016FB2462D8BF179AE71F7D702D09DEAC3F1D",
	};

	@Override
	public List<String> getBridges() {
		return Arrays.asList(BRIDGES);
	}

}
