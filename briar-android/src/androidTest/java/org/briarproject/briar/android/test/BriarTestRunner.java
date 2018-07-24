package org.briarproject.briar.android.test;

import android.app.Application;
import android.content.Context;
import android.support.test.runner.AndroidJUnitRunner;

import org.briarproject.briar.android.BriarTestApplication;

public class BriarTestRunner extends AndroidJUnitRunner {

	@Override
	public Application newApplication(ClassLoader cl, String className,
			Context context)
			throws InstantiationException, IllegalAccessException,
			ClassNotFoundException {
		return super.newApplication(cl, BriarTestApplication.class.getName(),
				context);
	}

}
