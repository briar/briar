package org.briarproject.briar.android.util;

import android.content.Context;
import android.widget.Toast;

class ToastUtils {
	static void showToast(String text, Context context) {
		Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
	}
}
