package org.briarproject.briar.android.forum;

import android.content.Intent;

import junit.framework.Assert;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.bramble.api.identity.Author;
import org.briarproject.bramble.api.sync.MessageId;
import org.briarproject.briar.android.TestBriarApplication;
import org.briarproject.briar.android.controller.handler.UiResultExceptionHandler;
import org.briarproject.briar.android.threaded.ThreadItemAdapter;
import org.briarproject.briar.android.threaded.ThreadItemList;
import org.briarproject.briar.android.threaded.ThreadItemListImpl;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.briarproject.bramble.api.identity.Author.Status.UNKNOWN;
import static org.briarproject.bramble.test.TestUtils.getAuthor;
import static org.briarproject.bramble.test.TestUtils.getRandomId;
import static org.briarproject.bramble.util.StringUtils.getRandomString;
import static org.briarproject.briar.api.forum.ForumConstants.MAX_FORUM_POST_BODY_LENGTH;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 21, application = TestBriarApplication.class,
		packageName = "org.briarproject.briar")
public class ForumActivityTest {

	private final static MessageId[] MESSAGE_IDS = new MessageId[6];

	static {
		for (int i = 0; i < MESSAGE_IDS.length; i++)
			MESSAGE_IDS[i] = new MessageId(getRandomId());
	}

	private final static MessageId[] PARENT_IDS = {
			null,
			MESSAGE_IDS[0],
			MESSAGE_IDS[1],
			MESSAGE_IDS[2],
			MESSAGE_IDS[0],
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
	private ArgumentCaptor<UiResultExceptionHandler<ThreadItemList<ForumItem>, DbException>>
			rc;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		Intent intent = new Intent();
		intent.putExtra("briar.GROUP_ID", getRandomId());
		forumActivity = Robolectric.buildActivity(TestForumActivity.class,
				intent).create().start().resume().get();
	}

	private ThreadItemList<ForumItem> getDummyData() {
		ForumItem[] forumItems = new ForumItem[6];
		for (int i = 0; i < forumItems.length; i++) {
			Author author = getAuthor();
			String content = getRandomString(MAX_FORUM_POST_BODY_LENGTH);
			forumItems[i] = new ForumItem(MESSAGE_IDS[i], PARENT_IDS[i],
					content, System.currentTimeMillis(), author, UNKNOWN);
			forumItems[i].setLevel(LEVELS[i]);
		}
		ThreadItemList<ForumItem> list = new ThreadItemListImpl<>();
		list.addAll(Arrays.asList(forumItems));
		return list;
	}

	@Test
	public void testNestedEntries() {
		ForumController mc = forumActivity.getController();
		ThreadItemList<ForumItem> dummyData = getDummyData();
		verify(mc, times(1)).loadItems(rc.capture());
		rc.getValue().onResult(dummyData);
		ThreadItemAdapter<ForumItem> adapter = forumActivity.getAdapter();
		Assert.assertNotNull(adapter);
		assertEquals(6, adapter.getItemCount());
		assertTrue(dummyData.get(0).getText()
				.equals(adapter.getItemAt(0).getText()));
		assertTrue(dummyData.get(1).getText()
				.equals(adapter.getItemAt(1).getText()));
		assertTrue(dummyData.get(2).getText()
				.equals(adapter.getItemAt(2).getText()));
		assertTrue(dummyData.get(3).getText()
				.equals(adapter.getItemAt(3).getText()));
		assertTrue(dummyData.get(4).getText()
				.equals(adapter.getItemAt(4).getText()));
		assertTrue(dummyData.get(5).getText()
				.equals(adapter.getItemAt(5).getText()));
	}

}
