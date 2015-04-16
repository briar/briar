package org.briarproject.android.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import android.annotation.SuppressLint;
import android.os.Build;

public class AndroidUtils {

	@SuppressLint("NewApi")
	@SuppressWarnings("deprecation")
	public static Collection<String> getSupportedArchitectures() {
		List<String> abis = new ArrayList<String>();
		if(Build.VERSION.SDK_INT >= 21) {
			for(String abi : Build.SUPPORTED_ABIS) abis.add(abi);
		} else if(Build.VERSION.SDK_INT >= 8) {
			abis.add(Build.CPU_ABI);
			if(Build.CPU_ABI2 != null) abis.add(Build.CPU_ABI2);
		} else {
			abis.add(Build.CPU_ABI);
		}
		return Collections.unmodifiableList(abis);
	}
}
