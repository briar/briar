package org.briarproject.briar.headless

import io.javalin.Javalin
import io.javalin.core.util.Header.AUTHORIZATION
import khttp.responses.Response
import org.briarproject.bramble.BrambleCoreEagerSingletons
import org.briarproject.bramble.api.crypto.CryptoComponent
import org.briarproject.briar.BriarCoreEagerSingletons
import org.briarproject.briar.api.test.TestDataCreator
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS
import java.io.File

const val port = 8000
const val url = "http://127.0.0.1:$port/v1"
const val token = "authToken"

@TestInstance(PER_CLASS)
abstract class IntegrationTest {

    private val dataDir = File("tmp")

    protected lateinit var api: Javalin
    protected lateinit var crypto: CryptoComponent
    protected lateinit var testDataCreator: TestDataCreator
    private lateinit var router: Router

    @BeforeAll
    fun setUp() {
        val app = DaggerBriarHeadlessTestApp.builder()
            .headlessTestModule(HeadlessTestModule(dataDir))
            .build()
        BrambleCoreEagerSingletons.Helper.injectEagerSingletons(app)
        BriarCoreEagerSingletons.Helper.injectEagerSingletons(app)
        HeadlessEagerSingletons.Helper.injectEagerSingletons(app)
        router = app.getRouter()
        crypto = app.getCryptoComponent()
        testDataCreator = app.getTestDataCreator()

        api = router.start(token, port, false)
    }

    @AfterAll
    fun tearDown() {
        router.stop()
        dataDir.deleteRecursively()
    }

    protected fun get(url: String): Response {
        return khttp.get(url, getAuthTokenHeader(token))
    }

    protected fun getWithWrongToken(url: String): Response {
        return khttp.get(url, getAuthTokenHeader("wrongToken"))
    }

    protected fun post(url: String, data: String): Response {
        return khttp.post(url, getAuthTokenHeader(token), data = data)
    }

    protected fun postWithWrongToken(url: String): Response {
        return khttp.post(url, getAuthTokenHeader("wrongToken"), data = "")
    }

    protected fun delete(url: String): Response {
        return khttp.delete(url, getAuthTokenHeader(token))
    }

    protected fun delete(url: String, data: String): Response {
        return khttp.delete(url, getAuthTokenHeader(token), data = data)
    }

    protected fun deleteWithWrongToken(url: String): Response {
        return khttp.delete(url, getAuthTokenHeader("wrongToken"))
    }

    private fun getAuthTokenHeader(token: String) = mapOf(Pair(AUTHORIZATION, "Bearer $token"))

}
