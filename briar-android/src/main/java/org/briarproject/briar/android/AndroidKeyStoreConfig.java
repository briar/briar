package org.briarproject.briar.android;

import android.security.keystore.KeyGenParameterSpec;

import org.briarproject.bramble.api.crypto.KeyStoreConfig;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.security.spec.AlgorithmParameterSpec;
import java.util.List;

import androidx.annotation.RequiresApi;

import static android.os.Build.VERSION.SDK_INT;
import static android.security.keystore.KeyProperties.PURPOSE_SIGN;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

@RequiresApi(23)
@NotNullByDefault
class AndroidKeyStoreConfig implements KeyStoreConfig {

	private final List<AlgorithmParameterSpec> specs;

	AndroidKeyStoreConfig() {
		KeyGenParameterSpec noStrongBox =
				new KeyGenParameterSpec.Builder("db", PURPOSE_SIGN)
						.setKeySize(256)
						.build();
		if (SDK_INT >= 28) {
			// Prefer StrongBox if available
			KeyGenParameterSpec strongBox =
					new KeyGenParameterSpec.Builder("db", PURPOSE_SIGN)
							.setIsStrongBoxBacked(true)
							.setKeySize(256)
							.build();
			specs = asList(strongBox, noStrongBox);
		} else {
			specs = singletonList(noStrongBox);
		}
	}

	@Override
	public String getKeyStoreType() {
		return "AndroidKeyStore";
	}

	@Override
	public String getKeyAlias() {
		return "db";
	}

	@Override
	public String getProviderName() {
		return "AndroidKeyStore";
	}

	@Override
	public String getMacAlgorithmName() {
		return "HmacSHA256";
	}

	@Override
	public List<AlgorithmParameterSpec> getParameterSpecs() {
		return specs;
	}
}
