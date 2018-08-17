package org.briarproject.briar.headless;

import org.briarproject.bramble.api.db.DbException;
import org.briarproject.briar.api.forum.Forum;
import org.briarproject.briar.api.forum.ForumManager;

import java.util.Collection;

import javax.inject.Inject;

import io.javalin.Context;

import static io.javalin.translator.json.JavalinJsonPlugin.getObjectToJsonMapper;

public class ForumController {

	private final ForumManager forumManager;

	@Inject
	public ForumController(ForumManager forumManager) {
		this.forumManager = forumManager;
	}

	public Context list(Context ctx) throws DbException {
		Collection<Forum> forums = forumManager.getForums();
		return ctx.result(getObjectToJsonMapper().map(forums));
	}

	public Context create(Context ctx) throws DbException {
		String name = ctx.formParam("name");
		if (name == null || name.length() < 1) {
			return ctx.status(500).result("Expecting Forum Name");
		} else {
			Forum forum = forumManager.addForum(name);
			return ctx.result(getObjectToJsonMapper().map(forum));
		}
	}

}
