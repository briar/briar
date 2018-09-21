package org.briarproject.briar.headless.forums

import io.javalin.BadRequestResponse
import io.mockk.every
import io.mockk.mockk
import org.briarproject.bramble.test.TestUtils.getRandomBytes
import org.briarproject.bramble.util.StringUtils.getRandomString
import org.briarproject.briar.api.forum.Forum
import org.briarproject.briar.api.forum.ForumConstants.MAX_FORUM_NAME_LENGTH
import org.briarproject.briar.api.forum.ForumManager
import org.briarproject.briar.headless.ControllerTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

internal class ForumControllerTest : ControllerTest() {

    private val forumManager = mockk<ForumManager>()

    private val controller = ForumControllerImpl(forumManager)

    private val forum = Forum(group, getRandomString(5), getRandomBytes(5))

    @Test
    fun list() {
        every { forumManager.forums } returns listOf(forum)
        every { ctx.json(listOf(forum.output())) } returns ctx

        controller.list(ctx)
    }

    @Test
    fun create() {
        every { ctx.formParam("text") } returns forum.name
        every { forumManager.addForum(forum.name) } returns forum
        every { ctx.json(forum.output()) } returns ctx

        controller.create(ctx)
    }

    @Test
    fun createNoName() {
        every { ctx.formParam("text") } returns null

        assertThrows(BadRequestResponse::class.java) { controller.create(ctx) }
    }

    @Test
    fun createEmptyName() {
        every { ctx.formParam("text") } returns ""

        assertThrows(BadRequestResponse::class.java) { controller.create(ctx) }
    }

    @Test
    fun createTooLongName() {
        every { ctx.formParam("text") } returns getRandomString(MAX_FORUM_NAME_LENGTH + 1)

        assertThrows(BadRequestResponse::class.java) { controller.create(ctx) }
    }

}
