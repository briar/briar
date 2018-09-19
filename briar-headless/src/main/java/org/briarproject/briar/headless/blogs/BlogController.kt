package org.briarproject.briar.headless.blogs

import io.javalin.BadRequestResponse
import io.javalin.Context
import org.briarproject.bramble.api.identity.IdentityManager
import org.briarproject.bramble.api.system.Clock
import org.briarproject.bramble.util.StringUtils
import org.briarproject.briar.api.blog.BlogConstants.MAX_BLOG_POST_BODY_LENGTH
import org.briarproject.briar.api.blog.BlogManager
import org.briarproject.briar.api.blog.BlogPostFactory
import javax.annotation.concurrent.Immutable
import javax.inject.Inject
import javax.inject.Singleton

@Immutable
@Singleton
class BlogController @Inject
constructor(
    private val blogManager: BlogManager,
    private val blogPostFactory: BlogPostFactory,
    private val identityManager: IdentityManager,
    private val clock: Clock
) {

    fun listPosts(ctx: Context): Context {
        val posts = blogManager.blogs.flatMap { blog ->
            blogManager.getPostHeaders(blog.id).map { header ->
                val body = blogManager.getPostBody(header.id)
                header.output(body)
            }
        }.sortedBy { it.timestampReceived }
        return ctx.json(posts)
    }

    fun createPost(ctx: Context): Context {
        val text = ctx.formParam("text")
        if (text == null || text.isEmpty())
            throw BadRequestResponse("Expecting blog post text")
        if (StringUtils.toUtf8(text).size > MAX_BLOG_POST_BODY_LENGTH)
            throw BadRequestResponse("Too long blog post text")

        val author = identityManager.localAuthor
        val blog = blogManager.getPersonalBlog(author)
        val now = clock.currentTimeMillis()
        val post = blogPostFactory.createBlogPost(blog.id, now, null, author, text)
        blogManager.addLocalPost(post)
        val header = blogManager.getPostHeader(blog.id, post.message.id)
        return ctx.json(header.output(text))
    }

}
