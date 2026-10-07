package com.abk.kernel.data.repository

import com.abk.kernel.data.api.GitHubApiService
import com.abk.kernel.data.model.KernelRunnerTarget
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.lang.reflect.Proxy

class KernelRunnerSettingsStoreTest {
    private lateinit var server: MockWebServer
    private lateinit var store: KernelRunnerSettingsStore
    private val gson = Gson()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GitHubApiService::class.java)
        store = KernelRunnerSettingsStore(api)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun readAbsentVariablesDefaultsToHosted() = runBlocking {
        enqueueWorkflows()
        enqueueVariables()
        val settings = success(store.read("owner", "repo", "dev"))
        assertTrue(settings.gki.supported)
        assertTrue(settings.oneplus.supported)
        assertFalse(settings.gki.selfHostedEnabled)
        assertEquals("", settings.gki.labels)
        assertEquals("/repos/owner/repo/contents/.github/workflows/build.yml?ref=dev", server.takeRequest().path)
        server.takeRequest()
        assertEquals("/repos/owner/repo/actions/variables?per_page=30&page=1", server.takeRequest().path)
    }

    @Test
    fun readUnconfiguredSwitchKeepsExistingCustomLabels() = runBlocking {
        enqueueWorkflows()
        enqueueVariables(mapOf("KERNEL_RUNNER" to "[\"self-hosted\",\"abk\"]", "ONEPLUS_RUNNER" to "oplus"))
        val settings = success(store.read("owner", "repo", "dev"))
        assertTrue(settings.gki.selfHostedEnabled)
        assertTrue(settings.oneplus.selfHostedEnabled)
        assertEquals("oplus", settings.oneplus.labels)
    }

    @Test
    fun readFalseOverridesSavedLabelsAndTrueDoesNotOverrideMissingLabels() = runBlocking {
        enqueueWorkflows()
        enqueueVariables(mapOf("KERNEL_RUNNER" to "abk", "KERNEL_SELF_HOSTED" to "FALSE", "ONEPLUS_SELF_HOSTED" to "true"))
        val settings = success(store.read("owner", "repo", "dev"))
        assertFalse(settings.gki.selfHostedEnabled)
        assertEquals("abk", settings.gki.labels)
        assertFalse(settings.oneplus.selfHostedEnabled)
    }

    @Test
    fun readHostedLabelDoesNotClaimSelfHosting() = runBlocking {
        enqueueWorkflows()
        enqueueVariables(mapOf("KERNEL_RUNNER" to "ubuntu-latest", "KERNEL_SELF_HOSTED" to "true"))
        assertFalse(success(store.read("owner", "repo", "dev")).gki.selfHostedEnabled)
    }

    @Test
    fun readUnsupportedWorkflowDoesNotClaimSettingsAreActive() = runBlocking {
        server.enqueue(MockResponse().setBody("# vars.KERNEL_RUNNER vars.KERNEL_SELF_HOSTED\njobs:\n  build:\n    runs-on: ubuntu-latest"))
        server.enqueue(MockResponse().setResponseCode(404))
        enqueueVariables(mapOf("KERNEL_RUNNER" to "abk"))
        val settings = success(store.read("owner", "repo", "dev"))
        assertFalse(settings.gki.supported)
        assertFalse(settings.gki.selfHostedEnabled)
        assertFalse(settings.oneplus.supported)
    }

    @Test
    fun readWorkflowAccessDeniedIsAnError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(403, (store.read("owner", "repo", "dev") as Result.Error).code)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun readVariableAccessDeniedIsNotAnEmptyConfiguration() = runBlocking {
        for (code in listOf(403, 404)) {
            enqueueWorkflows()
            server.enqueue(MockResponse().setResponseCode(code))
            assertEquals(code, (store.read("owner", "repo", "dev") as Result.Error).code)
        }
    }

    @Test
    fun readVariablesFollowsPagination() = runBlocking {
        enqueueWorkflows()
        enqueueVariables((1..30).associate { "OTHER_$it" to "value" }, totalCount = 31)
        enqueueVariables(mapOf("KERNEL_RUNNER" to "abk"), totalCount = 31)
        assertTrue(success(store.read("owner", "repo", "dev")).gki.selfHostedEnabled)
        repeat(3) { server.takeRequest() }
        assertEquals("/repos/owner/repo/actions/variables?per_page=30&page=2", server.takeRequest().path)
    }

    @Test
    fun readIncompleteVariableListIsAnError() = runBlocking {
        enqueueWorkflows()
        enqueueVariables(totalCount = 1)
        assertTrue(store.read("owner", "repo", "dev") is Result.Error)
    }

    @Test
    fun readResolvesDefaultBranchIfRefIsOmitted() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":1,"name":"repo","full_name":"owner/repo","fork":true,"private":false,"html_url":"https://github.com/owner/repo","default_branch":"custom"}"""))
        enqueueWorkflows()
        enqueueVariables()
        success(store.read("owner", "repo", null))
        assertEquals("/repos/owner/repo", server.takeRequest().path)
        assertTrue(server.takeRequest().path.orEmpty().endsWith("?ref=custom"))
    }

    @Test
    fun saveCreatesLabelsBeforeEnablingWithoutDispatching() = runBlocking {
        enqueueWorkflow(KernelRunnerTarget.GKI)
        enqueueVariables()
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(201))
        val saved = success(store.save("owner", "repo", KernelRunnerTarget.GKI, " [\"self-hosted\", \"abk\"] ", true, "dev"))
        assertTrue(saved.selfHostedEnabled)
        assertEquals("[\"self-hosted\",\"abk\"]", saved.labels)
        repeat(2) { server.takeRequest() }
        val labels = server.takeRequest()
        assertEquals("POST", labels.method)
        assertEquals("/repos/owner/repo/actions/variables", labels.path)
        assertEquals("KERNEL_RUNNER", parse(labels.body.readUtf8()).get("name").asString)
        val switch = server.takeRequest()
        val body = parse(switch.body.readUtf8())
        assertEquals("KERNEL_SELF_HOSTED", body.get("name").asString)
        assertEquals("true", body.get("value").asString)
        assertTrue(body.get("value").asJsonPrimitive.isString)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun saveUpdatesOnePlusVariablesOnlyAndKeepsLabelsWhenDisabled() = runBlocking {
        enqueueWorkflow(KernelRunnerTarget.ONEPLUS)
        enqueueVariables(mapOf("ONEPLUS_RUNNER" to "oplus", "ONEPLUS_SELF_HOSTED" to "true", "KERNEL_RUNNER" to "gki"))
        server.enqueue(MockResponse().setResponseCode(204))
        val saved = success(store.save("owner", "repo", KernelRunnerTarget.ONEPLUS, "oplus", false, "dev"))
        assertFalse(saved.selfHostedEnabled)
        assertEquals("oplus", saved.labels)
        repeat(2) { server.takeRequest() }
        val switch = server.takeRequest()
        assertEquals("PATCH", switch.method)
        assertTrue(switch.path.orEmpty().endsWith("/ONEPLUS_SELF_HOSTED"))
        assertEquals("false", parse(switch.body.readUtf8()).get("value").asString)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun saveDisabledWithBlankLabelsPreservesExistingSelection() = runBlocking {
        enqueueWorkflow(KernelRunnerTarget.GKI)
        enqueueVariables(mapOf("KERNEL_RUNNER" to "abk"))
        server.enqueue(MockResponse().setResponseCode(201))
        val saved = success(store.save("owner", "repo", KernelRunnerTarget.GKI, "", false, "dev"))
        assertEquals("abk", saved.labels)
        assertFalse(saved.selfHostedEnabled)
        assertEquals(3, server.requestCount)
        repeat(2) { server.takeRequest() }
        assertEquals("KERNEL_SELF_HOSTED", parse(server.takeRequest().body.readUtf8()).get("name").asString)
    }

    @Test
    fun saveDisabledIgnoresMalformedDraftAndPreservesMalformedStoredLabels() = runBlocking {
        enqueueWorkflow(KernelRunnerTarget.GKI)
        enqueueVariables(mapOf("KERNEL_RUNNER" to "[broken", "KERNEL_SELF_HOSTED" to "true"))
        server.enqueue(MockResponse().setResponseCode(204))
        val saved = success(store.save("owner", "repo", KernelRunnerTarget.GKI, "[]", false, "dev"))
        assertEquals("[broken", saved.labels)
        assertFalse(saved.selfHostedEnabled)
        assertEquals(3, server.requestCount)
        repeat(2) { server.takeRequest() }
        assertTrue(server.takeRequest().path.orEmpty().endsWith("/KERNEL_SELF_HOSTED"))
    }

    @Test
    fun saveDisabledDoesNotSwitchToDraftMachineFirst() = runBlocking {
        enqueueWorkflow(KernelRunnerTarget.GKI)
        enqueueVariables(mapOf("KERNEL_RUNNER" to "existing", "KERNEL_SELF_HOSTED" to "true"))
        server.enqueue(MockResponse().setResponseCode(204))
        val saved = success(store.save("owner", "repo", KernelRunnerTarget.GKI, "different", false, "dev"))
        assertEquals("existing", saved.labels)
        assertEquals(3, server.requestCount)
        repeat(2) { server.takeRequest() }
        assertTrue(server.takeRequest().path.orEmpty().endsWith("/KERNEL_SELF_HOSTED"))
    }

    @Test
    fun saveRejectsInvalidOrHostedLabelsBeforeAnyNetworkWrite() = runBlocking {
        val invalid = listOf("", "true", "false", "null", "[]", "[1]", "[true]", "[\"\"]", "[\"   \"]", "['abk']", "[\"abk\",]", "{}", "self-hosted,abk", "abk\nrunner", "ubuntu-latest", "windows-2025", "macos-15", "[\"ubuntu-latest\"]")
        invalid.forEach { labels ->
            assertTrue("Expected error for $labels", store.save("owner", "repo", KernelRunnerTarget.GKI, labels, true, "dev") is Result.Error)
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun saveUnsupportedWorkflowPerformsNoVariableWrites() = runBlocking {
        server.enqueue(MockResponse().setBody("jobs:\n  build:\n    runs-on: ubuntu-latest"))
        assertTrue(store.save("owner", "repo", KernelRunnerTarget.GKI, "abk", true, "dev") is Result.Error)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun saveVariableReadFailurePerformsNoWrites() = runBlocking {
        enqueueWorkflow(KernelRunnerTarget.GKI)
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(403, (store.save("owner", "repo", KernelRunnerTarget.GKI, "abk", true, "dev") as Result.Error).code)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun saveLabelFailureDoesNotEnableSwitch() = runBlocking {
        enqueueWorkflow(KernelRunnerTarget.GKI)
        enqueueVariables()
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(403, (store.save("owner", "repo", KernelRunnerTarget.GKI, "abk", true, "dev") as Result.Error).code)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun saveSwitchFailureReportsPartialSave() = runBlocking {
        enqueueWorkflow(KernelRunnerTarget.GKI)
        enqueueVariables()
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(403))
        val error = store.save("owner", "repo", KernelRunnerTarget.GKI, "abk", true, "dev") as Result.Error
        assertEquals(403, error.code)
        assertTrue(error.message.contains("may already be saved"))
    }

    @Test
    fun saveCreateRaceRetriesAsUpdate() = runBlocking {
        enqueueWorkflow(KernelRunnerTarget.GKI)
        enqueueVariables()
        server.enqueue(MockResponse().setResponseCode(422))
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(201))
        success(store.save("owner", "repo", KernelRunnerTarget.GKI, "abk", true, "dev"))
        repeat(3) { server.takeRequest() }
        assertEquals("PATCH", server.takeRequest().method)
    }

    @Test
    fun saveDeleteRaceRetriesAsCreate() = runBlocking {
        enqueueWorkflow(KernelRunnerTarget.GKI)
        enqueueVariables(mapOf("KERNEL_RUNNER" to "old"))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setResponseCode(201))
        server.enqueue(MockResponse().setResponseCode(201))
        success(store.save("owner", "repo", KernelRunnerTarget.GKI, "abk", true, "dev"))
        repeat(3) { server.takeRequest() }
        assertEquals("POST", server.takeRequest().method)
    }

    @Test
    fun saveFailedRaceRetryDoesNotReportSuccess() = runBlocking {
        enqueueWorkflow(KernelRunnerTarget.GKI)
        enqueueVariables()
        server.enqueue(MockResponse().setResponseCode(422))
        server.enqueue(MockResponse().setResponseCode(403))
        assertEquals(403, (store.save("owner", "repo", KernelRunnerTarget.GKI, "abk", true, "dev") as Result.Error).code)
        assertEquals(4, server.requestCount)
    }

    @Test
    fun cancellationIsNotConvertedIntoAnErrorResult() {
        val api = Proxy.newProxyInstance(
            GitHubApiService::class.java.classLoader,
            arrayOf(GitHubApiService::class.java)
        ) { _, _, _ -> throw CancellationException("cancelled") } as GitHubApiService
        val cancelledStore = KernelRunnerSettingsStore(api)
        assertThrows(CancellationException::class.java) {
            runBlocking { cancelledStore.read("owner", "repo", "dev") }
        }
    }

    private fun enqueueWorkflows() = KernelRunnerTarget.entries.forEach(::enqueueWorkflow)

    private fun enqueueWorkflow(target: KernelRunnerTarget) {
        server.enqueue(MockResponse().setBody("jobs:\n  build:\n    runs-on: \${{ vars.${target.switchVariable} == 'false' && 'ubuntu-latest' || vars.${target.runnerVariable} || 'ubuntu-latest' }}"))
    }

    private fun enqueueVariables(values: Map<String, String> = emptyMap(), totalCount: Int = values.size) {
        server.enqueue(MockResponse().setBody(gson.toJson(mapOf("total_count" to totalCount, "variables" to values.map { (key, value) -> mapOf("name" to key, "value" to value) }))))
    }

    private fun parse(value: String): JsonObject = gson.fromJson(value, JsonObject::class.java)

    private fun <T> success(result: Result<T>): T {
        assertTrue("Expected success, got $result", result is Result.Success<T>)
        return (result as Result.Success<T>).data
    }
}
