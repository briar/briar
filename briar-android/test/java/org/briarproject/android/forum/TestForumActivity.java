package org.briarproject.android.forum;

import org.briarproject.android.ActivityModule;
import org.briarproject.android.controller.BriarController;
import org.briarproject.android.controller.BriarControllerImpl;
import org.briarproject.android.threaded.ThreadItemAdapter;
import org.mockito.Mockito;

/**
 * This class exposes the ForumController and offers the possibility to
 * override it.
 */
public class TestForumActivity extends ForumActivity {

	public ForumController getController() {
		return forumController;
	}

	public ThreadItemAdapter<ForumItem> getAdapter() {
		return adapter;
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

			@Override
			protected ForumController provideForumController(
					ForumControllerImpl forumController) {
				return Mockito.mock(ForumController.class);
			}
		};
	}
}
