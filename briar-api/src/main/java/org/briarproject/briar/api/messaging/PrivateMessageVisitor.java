package org.briarproject.briar.api.messaging;

import org.briarproject.briar.api.blog.BlogInvitationRequest;
import org.briarproject.briar.api.blog.BlogInvitationResponse;
import org.briarproject.briar.api.forum.ForumInvitationRequest;
import org.briarproject.briar.api.forum.ForumInvitationResponse;
import org.briarproject.briar.api.introduction.IntroductionRequest;
import org.briarproject.briar.api.introduction.IntroductionResponse;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationRequest;
import org.briarproject.briar.api.privategroup.invitation.GroupInvitationResponse;

public interface PrivateMessageVisitor {

	void visitPrivateMessageHeader(PrivateMessageHeader h);

	void visitBlogInvitatioRequest(BlogInvitationRequest r);

	void visitBlogInvitationResponse(BlogInvitationResponse r);

	void visitForumInvitationRequest(ForumInvitationRequest r);

	void visitForumInvitationResponse(ForumInvitationResponse r);

	void visitGroupInvitationRequest(GroupInvitationRequest r);

	void visitGroupInvitationResponse(GroupInvitationResponse r);

	void visitIntroductionRequest(IntroductionRequest r);

	void visitIntroductionResponse(IntroductionResponse r);
}
