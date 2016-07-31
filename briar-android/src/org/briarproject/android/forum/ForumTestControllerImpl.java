package org.briarproject.android.forum;

import org.briarproject.android.controller.handler.ResultHandler;
import org.briarproject.api.UniqueId;
import org.briarproject.api.forum.Forum;
import org.briarproject.api.identity.AuthorId;
import org.briarproject.api.sync.GroupId;
import org.briarproject.api.sync.MessageId;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import javax.inject.Inject;

import static org.briarproject.api.identity.Author.Status.UNVERIFIED;

public class ForumTestControllerImpl implements ForumController {

	private static final Logger LOG =
			Logger.getLogger(ForumControllerImpl.class.getName());

	private final static String[] AUTHORS = {
			"Guðmundur",
			"Jónas",
			"Geir Þorsteinn Gísli Máni Halldórsson Guðjónsson Mogensen",
			"Baldur Friðrik",
			"Anna Katrín",
			"Þór",
			"Anna Þorbjörg",
			"Guðrún",
			"Helga",
			"Haraldur"
	};

	private final static AuthorId[] AUTHOR_ID = new AuthorId[AUTHORS.length];

	static {
		SecureRandom random = new SecureRandom();
		for (int i = 0; i < AUTHOR_ID.length; i++) {
			byte[] b = new byte[UniqueId.LENGTH];
			random.nextBytes(b);
			AUTHOR_ID[i] = new AuthorId(b);

		}
	}

	private final static String SAGA =
			"Það er upphaf á sögu þessari að Hákon konungur " +
					"Aðalsteinsfóstri réð fyrir Noregi og var þetta á ofanverðum " +
					"hans dögum. Þorkell hét maður; hann var kallaður skerauki; " +
					"hann bjó í Súrnadal og var hersir að nafnbót. Hann átti sér " +
					"konu er Ísgerður hét og sonu þrjá barna; hét einn Ari, annar " +
					"Gísli, þriðji Þorbjörn, hann var þeirra yngstur, og uxu allir " +
					"upp heima þar. " +
					"Maður er nefndur Ísi; hann bjó í firði er Fibuli heitir á " +
					"Norðmæri; kona hans hét Ingigerður en Ingibjörg dóttir. Ari, " +
					"sonur Þorkels Sýrdæls, biður hennar og var hún honum gefin " +
					"með miklu fé. Kolur hét þræll er í brott fór með henni.";

	private ForumEntry[] forumEntries;

	@Inject
	ForumTestControllerImpl() {

	}

	private void textRandomize(SecureRandom random, int[] i) {
		for (int e = 0; e < forumEntries.length; e++) {
			// select a random white-space for the cut-off
			do {
				i[e] = Math.abs(random.nextInt() % (SAGA.length()));
			} while (SAGA.charAt(i[e]) != ' ');
		}
	}

	private int levelRandomize(SecureRandom random, int[] l) {
		int maxl = 0;
		int lastl = 0;
		l[0] = 0;
		for (int e = 1; e < forumEntries.length; e++) {
			// select random level 1-10
			do {
				l[e] = Math.abs(random.nextInt() % 10);
			} while (l[e] > lastl + 1);
			lastl = l[e];
			if (lastl > maxl)
				maxl = lastl;
		}
		return maxl;
	}

	@Override
	public void loadForum(GroupId groupId,
			ResultHandler<Boolean> resultHandler) {
		SecureRandom random = new SecureRandom();
		forumEntries = new ForumEntry[100];
		// string cut off index
		int[] i = new int[forumEntries.length];
		// entry discussion level
		int[] l = new int[forumEntries.length];

		textRandomize(random, i);
		int maxLevel;
		// make sure we get a deep discussion
		do {
			maxLevel = levelRandomize(random, l);
		} while (maxLevel < 6);
		for (int e = 0; e < forumEntries.length; e++) {
			int authorIndex = Math.abs(random.nextInt() % AUTHORS.length);
			long timestamp =
					System.currentTimeMillis() - Math.abs(random.nextInt());
			byte[] b = new byte[UniqueId.LENGTH];
			random.nextBytes(b);
			forumEntries[e] =
					new ForumEntry(new MessageId(b), SAGA.substring(0, i[e]),
							l[e], timestamp, AUTHORS[authorIndex],
							AUTHOR_ID[authorIndex], UNVERIFIED);
		}
		LOG.info("forum entries: " + forumEntries.length);
		resultHandler.onResult(true);
	}

	@Override
	public Forum getForum() {
		return null;
	}

	@Override
	public List<ForumEntry> getForumEntries() {
		return forumEntries == null ? null :
				new ArrayList<>(Arrays.asList(forumEntries));
	}

	@Override
	public void unsubscribe(ResultHandler<Boolean> resultHandler) {

	}

	@Override
	public void entryRead(ForumEntry forumEntry) {

	}

	@Override
	public void entriesRead(Collection<ForumEntry> messageIds) {

	}

	@Override
	public void createPost(byte[] body) {

	}

	@Override
	public void createPost(byte[] body, MessageId parentId) {

	}

	@Override
	public void onActivityCreate() {

	}

	@Override
	public void onActivityResume() {

	}

	@Override
	public void onActivityPause() {

	}

	@Override
	public void onActivityDestroy() {

	}
}
