package org.briarproject.briar.android.forum;

import android.os.Bundle;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityModule;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.controller.BriarController;
import org.briarproject.briar.android.controller.BriarControllerImpl;
import org.briarproject.briar.android.threaded.ThreadItemAdapter;
import org.mockito.Mockito;

import javax.annotation.Nullable;

/**
 * This class exposes the ForumController and offers the possibility to
 * override it.
 */
@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class TestForumActivity extends ForumActivity {

	@Override
	public ForumController getController() {
		return forumController;
	}

	public ThreadItemAdapter<ForumItem> getAdapter() {
		return adapter;
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		setTheme(R.style.BriarTheme_NoActionBar);
		super.onCreate(state);
	}

	@Override
	protected ActivityModule getActivityModule() {
		return new ActivityModule(this) {

			@Override
			protected BriarController provideBriarController(
					BriarControllerImpl briarController) {
				BriarController c = Mockito.mock(BriarController.class);
				Mockito.when(c.hasEncryptionKey()).thenReturn(true);
				return c;
			}

		};
	}

	@Override
	protected ForumModule getForumModule() {
		return new ForumModule() {
			@Override
			ForumController provideForumController(BaseActivity activity,
					ForumControllerImpl forumController) {
				return Mockito.mock(ForumController.class);
			}
		};
	}
}
