package com.serendeep.marginalia.ai

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelResolverTest {
    private val catalog = listOf(ChatModel("gpt-5.5", "GPT-5.5"), ChatModel("gpt-5.5-mini", "GPT-5.5 mini", listOf(Effort.LOW, Effort.MEDIUM)))

    private fun resolve(task: AiTask, override: TaskModel = TaskModel(), global: String? = "gpt-5.5", models: List<ChatModel> = catalog) =
        ModelResolver.resolve(task, override, global, models)

    @Test
    fun explicitOverrideWins() {
        val r = resolve(AiTask.AUTO_SORT, TaskModel("gpt-5.5", Effort.HIGH))
        assertEquals(ResolvedModel("gpt-5.5", Effort.HIGH), r)
    }

    @Test
    fun autoSortPicksFirstFastModelWithLowEffort() {
        assertEquals(ResolvedModel("gpt-5.5-mini", Effort.LOW), resolve(AiTask.AUTO_SORT))
        assertEquals(null, resolve(AiTask.AUTO_SORT, models = listOf(ChatModel("gpt-4.1-mini", "4.1 mini"))).effort)
        val flash = listOf(ChatModel("big", "Big"), ChatModel("x-flash", "Flash"), ChatModel("y-nano", "Nano"))
        assertEquals("x-flash", resolve(AiTask.AUTO_SORT, models = flash).model)
    }

    @Test
    fun fallsBackToGlobalModel() {
        assertEquals("gpt-5.5", resolve(AiTask.AUTO_SORT, models = listOf(ChatModel("gpt-5.5", "GPT-5.5"))).model)
        assertEquals("gpt-5.5", resolve(AiTask.AUTO_SORT, models = emptyList()).model)
        assertEquals(ResolvedModel("gpt-5.5", null), resolve(AiTask.ASK))
        assertEquals(ResolvedModel(null, null), resolve(AiTask.EXPLAIN, global = null))
    }

    @Test
    fun smartTasksIgnoreFastModels() {
        assertEquals("gpt-5.5", resolve(AiTask.SUMMARIZE).model)
    }

    @Test
    fun effortOverrideReplacesAutoSortDefault() {
        assertEquals(Effort.MEDIUM, resolve(AiTask.AUTO_SORT, TaskModel(effort = Effort.MEDIUM)).effort)
    }

    @Test
    fun reasoningInRequestBodyOnlyWhenSet() {
        val r = AiRequest("i", "t")
        assertFalse(ResponsesClient.requestBody("m", r, null).has("reasoning"))
        assertEquals("high", ResponsesClient.requestBody("m", r, Effort.HIGH).getJSONObject("reasoning").getString("effort"))
        assertFalse(OpenAiCompatibleClient.requestBody("m", r, null).has("reasoning_effort"))
        assertEquals("minimal", OpenAiCompatibleClient.requestBody("m", r, Effort.MINIMAL).getString("reasoning_effort"))
    }

    @Test
    fun supportedLevelsParsedFromObjectsAndStrings() {
        val models = ResponsesClient.parseModels(
            """{"models":[{"slug":"a","visibility":"list","supported_reasoning_levels":[{"effort":"low"},{"effort":"high","description":"x"}]},
               {"slug":"b","visibility":"list","supported_reasoning_levels":["minimal","medium","bogus"]},
               {"slug":"c","visibility":"list"}]}""",
        )
        assertEquals(listOf(Effort.LOW, Effort.HIGH), models[0].efforts)
        assertEquals(listOf(Effort.MINIMAL, Effort.MEDIUM), models[1].efforts)
        assertTrue(models[2].efforts.isEmpty())
        val compatible = OpenAiCompatibleClient.parseModels("""{"data":[{"id":"m","supported_reasoning_levels":["low"]}]}""")
        assertEquals(listOf(Effort.LOW), compatible.single().efforts)
        assertTrue(ResponsesClient.parseEfforts(JSONArray()).isEmpty())
    }

    @Test
    fun everyPromptSetsItsTask() {
        assertEquals(AiTask.EXPLAIN, Prompts.explainPage("p").task)
        assertEquals(AiTask.SUMMARIZE, Prompts.summarizeDocument(listOf("p"), "t").task)
        assertEquals(AiTask.CARDS, Prompts.generateCards("p").task)
        assertEquals(AiTask.ASK, Prompts.askLibrary("q", emptyList()).task)
        assertEquals(AiTask.AUTO_SORT, Prompts.sortDocument(emptyList(), "t", "f.pdf", null, listOf("p")).task)
    }

    @Test
    fun fastModelIsPickedFromTheDescription() {
        val plan = listOf(
            ChatModel("gpt-6.1-sol", "GPT-6.1-Sol", description = "Latest workhorse model for coding and everyday work."),
            ChatModel("gpt-6-astra", "GPT-6-Astra", description = "Frontier intelligence for the most demanding work."),
            ChatModel("gpt-6-luna", "GPT-6-Luna", description = "Fast and affordable model for easier tasks."),
            ChatModel("gpt-5.6-luna", "GPT-5.6-Luna", description = "Older fast and efficient model."),
        )
        assertEquals("gpt-6-luna", ModelResolver.fastModel(plan)?.slug)
    }
}
