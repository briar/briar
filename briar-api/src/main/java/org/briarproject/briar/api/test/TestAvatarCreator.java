package org.briarproject.briar.api.test;

import java.io.InputStream;

import javax.annotation.Nullable;

public interface TestAvatarCreator {
	@Nullable
	InputStream getAvatarInputStream();
}
