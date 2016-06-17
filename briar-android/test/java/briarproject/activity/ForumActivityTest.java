package briarproject.activity;

import android.content.Intent;

import junit.framework.Assert;

import org.briarproject.BuildConfig;
import org.briarproject.TestUtils;
import org.briarproject.android.controller.handler.UiResultHandler;
import org.briarproject.android.forum.ForumActivity;
import org.briarproject.android.forum.ForumController;
import org.briarproject.android.forum.ForumEntry;
import org.briarproject.api.identity.Author;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricGradleTestRunner.class)
@Config(constants = BuildConfig.class, sdk = 21,
		application = TestBriarApplication.class)
public class ForumActivityTest {

	private final static String AUTHOR_1 = "Author 1";
	private final static String AUTHOR_2 = "Author 2";
	private final static String AUTHOR_3 = "Author 3";
	private final static String AUTHOR_4 = "Author 4";
	private final static String AUTHOR_5 = "Author 5";
	private final static String AUTHOR_6 = "Author 6";

	private final static String[] AUTHORS = {
			AUTHOR_1, AUTHOR_2, AUTHOR_3, AUTHOR_4, AUTHOR_5, AUTHOR_6
	};

	/*
		1
		-> 2
		   -> 3
			  -> 4
		   5
		6
	 */
	private final static int[] LEVELS = {
			0, 1, 2, 3, 1, 0
	};

	private TestForumActivity forumActivity;
	@Captor
	private ArgumentCaptor<UiResultHandler<Boolean>> rc;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		Intent intent = new Intent();
		intent.putExtra("briar.GROUP_ID", TestUtils.getRandomId());
		forumActivity = Robolectric.buildActivity(TestForumActivity.class)
				.withIntent(intent).create().resume().get();
	}


	private List<ForumEntry> getDummyData() {
		ForumEntry[] forumEntries = new ForumEntry[6];
		for (int i = 0; i < forumEntries.length; i++) {
			forumEntries[i] =
					new ForumEntry(new MessageId(TestUtils.getRandomId()),
							AUTHORS[i], LEVELS[i], System.currentTimeMillis(),
							AUTHORS[i], new AuthorId(TestUtils.getRandomId()),
							Author.Status.UNKNOWN);
		}
		return new ArrayList<>(Arrays.asList(forumEntries));
	}

	@Test
	public void testNestedEntries() {
		ForumController mc = forumActivity.getController();
		List<ForumEntry> dummyData = getDummyData();
		Mockito.when(mc.getForumEntries()).thenReturn(dummyData);
		// Verify that the forum load is called once
		verify(mc, times(1))
				.loadForum(Mockito.any(GroupId.class), rc.capture());
		rc.getValue().onResult(true);
		verify(mc, times(1)).getForumEntries();
		ForumActivity.ForumAdapter adapter = forumActivity.getAdapter();
		Assert.assertNotNull(adapter);
		// Cascade close
		assertEquals(6, adapter.getItemCount());
		adapter.hideDescendants(dummyData.get(2));
		assertEquals(5, adapter.getItemCount());
		adapter.hideDescendants(dummyData.get(1));
		assertEquals(4, adapter.getItemCount());
		adapter.hideDescendants(dummyData.get(0));
		assertEquals(2, adapter.getItemCount());
		assertTrue(dummyData.get(0).getText()
				.equals(adapter.getVisibleEntry(0).getText()));
		assertTrue(dummyData.get(5).getText()
				.equals(adapter.getVisibleEntry(1).getText()));
		// Cascade re-open
		adapter.showDescendants(dummyData.get(0));
		assertEquals(4, adapter.getItemCount());
		adapter.showDescendants(dummyData.get(1));
		assertEquals(5, adapter.getItemCount());
		adapter.showDescendants(dummyData.get(2));
		assertEquals(6, adapter.getItemCount());
		assertTrue(dummyData.get(2).getText()
				.equals(adapter.getVisibleEntry(2).getText()));
		assertTrue(dummyData.get(4).getText()
				.equals(adapter.getVisibleEntry(4).getText()));
	}
}
