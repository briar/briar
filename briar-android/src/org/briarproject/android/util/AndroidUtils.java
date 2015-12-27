package org.briarproject.android.util;

import android.annotation.SuppressLint;
import android.os.Build;
import android.support.design.widget.TextInputLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class AndroidUtils {

	@SuppressLint("NewApi")
	@SuppressWarnings("deprecation")
	public static Collection<String> getSupportedArchitectures() {
		List<String> abis = new ArrayList<String>();
		if (Build.VERSION.SDK_INT >= 21) {
			abis.addAll(Arrays.asList(Build.SUPPORTED_ABIS));
		} else {
			abis.add(Build.CPU_ABI);
			if (Build.CPU_ABI2 != null) abis.add(Build.CPU_ABI2);
		}
		return Collections.unmodifiableList(abis);
	}

	public static void setError(TextInputLayout til, String error, boolean condition) {
		if (condition) {
			if (til.getError() == null)
				til.setError(error);
		} else
			til.setError(null);
	}
}
