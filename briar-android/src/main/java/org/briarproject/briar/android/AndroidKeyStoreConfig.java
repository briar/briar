package org.briarproject.briar.android;

import android.security.keystore.KeyGenParameterSpec;

import org.briarproject.bramble.api.crypto.KeyStoreConfig;
import org.briarproject.bramble.api.nullsafety.NotNullByDefault;

import java.security.spec.AlgorithmParameterSpec;

import androidx.annotation.RequiresApi;

import static android.security.keystore.KeyProperties.PURPOSE_SIGN;
import static android.security.keystore.KeyProperties.PURPOSE_VERIFY;

@RequiresApi(23)
@NotNullByDefault
class AndroidKeyStoreConfig implements KeyStoreConfig {

	private final KeyGenParameterSpec spec;

	AndroidKeyStoreConfig() {
		int purposes = PURPOSE_SIGN | PURPOSE_VERIFY;
		spec = new KeyGenParameterSpec.Builder("db", purposes)
				.setKeySize(256)
				.build();
	}

	@Override
	public String getKeyStoreType() {
		return "AndroidKeyStore";
	}

	@Override
	public String getAlias() {
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
	public AlgorithmParameterSpec getParameterSpec() {
		return spec;
	}
}
