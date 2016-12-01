package org.briarproject.briar.android.forum;

import android.content.Intent;

import junit.framework.Assert;

import org.briarproject.TestUtils;
import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.BuildConfig;
import org.briarproject.briar.android.TestBriarApplication;
import org.briarproject.briar.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.briar.android.threaded.ThreadItemAdapter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.briarproject.bramble.api.identity.Author.Status.UNKNOWN;
import static org.briarproject.bramble.api.identity.AuthorConstants.MAX_PUBLIC_KEY_LENGTH;
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

	private final static MessageId[] AUTHOR_IDS = new MessageId[AUTHORS.length];

	static {
		for (int i = 0; i < AUTHOR_IDS.length; i++)
			AUTHOR_IDS[i] = new MessageId(TestUtils.getRandomId());
	}

	private final static MessageId[] PARENT_AUTHOR_IDS = {
			null,
			AUTHOR_IDS[0],
			AUTHOR_IDS[1],
			AUTHOR_IDS[2],
			AUTHOR_IDS[0],
			null
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
	private ArgumentCaptor<UiResultExceptionHandler<Collection<ForumItem>, DbException>>
			rc;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		Intent intent = new Intent();
		intent.putExtra("briar.GROUP_ID", TestUtils.getRandomId());
		forumActivity = Robolectric.buildActivity(TestForumActivity.class)
				.withIntent(intent).create().resume().get();
	}

	private List<ForumItem> getDummyData() {
		ForumItem[] forumItems = new ForumItem[6];
		for (int i = 0; i < forumItems.length; i++) {
			AuthorId authorId = new AuthorId(TestUtils.getRandomId());
			byte[] publicKey = TestUtils.getRandomBytes(MAX_PUBLIC_KEY_LENGTH);
			Author author = new Author(authorId, AUTHORS[i], publicKey);
			forumItems[i] = new ForumItem(AUTHOR_IDS[i], PARENT_AUTHOR_IDS[i],
					AUTHORS[i], System.currentTimeMillis(), author, UNKNOWN);
			forumItems[i].setLevel(LEVELS[i]);
		}
		return new ArrayList<>(Arrays.asList(forumItems));
	}

	@Test
	public void testNestedEntries() {
		ForumController mc = forumActivity.getController();
		List<ForumItem> dummyData = getDummyData();
		verify(mc, times(1)).loadItems(rc.capture());
		rc.getValue().onResult(dummyData);
		ThreadItemAdapter<ForumItem> adapter = forumActivity.getAdapter();
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
				.equals(adapter.getVisibleItem(0).getText()));
		assertTrue(dummyData.get(5).getText()
				.equals(adapter.getVisibleItem(1).getText()));
		// Cascade re-open
		adapter.showDescendants(dummyData.get(0));
		assertEquals(4, adapter.getItemCount());
		adapter.showDescendants(dummyData.get(1));
		assertEquals(5, adapter.getItemCount());
		adapter.showDescendants(dummyData.get(2));
		assertEquals(6, adapter.getItemCount());
		assertTrue(dummyData.get(2).getText()
				.equals(adapter.getVisibleItem(2).getText()));
		assertTrue(dummyData.get(4).getText()
				.equals(adapter.getVisibleItem(4).getText()));
	}
}
