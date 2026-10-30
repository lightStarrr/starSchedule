package com.star.schedule.feature.timetable.presentation

import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.DayNoteEntity
import com.star.schedule.core.database.LessonTimeEntity
import com.star.schedule.core.database.LessonTimeTemplateEntity
import com.star.schedule.core.database.LessonTimeTemplateItemEntity
import com.star.schedule.core.database.TimetableEntity
import com.star.schedule.feature.schedule.domain.ScheduleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 模板列表的删除与撤销。
 *
 * 这里锁两件事：列表里的「共几节、几点到几点」是由节次算出来的；
 * 撤销必须把模板连同它的节次一起按原 id 插回去，否则恢复出来的模板是空的。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LessonTimeTemplatesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeTemplateRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository.seed(
            template = LessonTimeTemplateEntity(
                id = 7L,
                name = "早八作息",
                createdAt = 1L,
                updatedAt = 1L,
            ),
            items = listOf(
                LessonTimeTemplateItemEntity(
                    id = 1L,
                    templateId = 7L,
                    period = 1,
                    startTime = "08:00",
                    endTime = "08:45",
                ),
                LessonTimeTemplateItemEntity(
                    id = 2L,
                    templateId = 7L,
                    period = 2,
                    startTime = "08:55",
                    endTime = "09:40",
                ),
            ),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a template row summarizes its period count and last update time`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val row = viewModel.uiState.value.rows.single()
        assertEquals("早八作息", row.name)
        assertEquals(2, row.periodCount)
        assertEquals(1L, row.updatedAt)
        assertTrue(!viewModel.uiState.value.isLoading)
    }

    @Test
    fun `deleting a template exposes an undo that restores it with its periods`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            viewModel.delete(viewModel.uiState.value.rows.single())
            advanceUntilIdle()

            assertTrue(repository.isGone(7L))
            assertTrue(viewModel.uiState.value.rows.isEmpty())
            val undo = requireNotNull(viewModel.uiState.value.undo)
            assertEquals("早八作息", undo.name)

            viewModel.undoDelete(undo.token)
            advanceUntilIdle()

            assertEquals(listOf(7L), repository.restoredTemplateIds)
            assertEquals(2, repository.itemCount(7L))
            assertEquals(1, viewModel.uiState.value.rows.size)
            assertNull(viewModel.uiState.value.undo)
        }

    @Test
    fun `a late undo of the previous deletion restores nothing`() = runTest(dispatcher) {
        repository.seed(
            template = LessonTimeTemplateEntity(id = 8L, name = "晚课", createdAt = 2L, updatedAt = 2L),
            items = emptyList(),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.delete(viewModel.uiState.value.rows.first { it.id == 7L })
        advanceUntilIdle()
        val first = requireNotNull(viewModel.uiState.value.undo)

        viewModel.delete(viewModel.uiState.value.rows.first { it.id == 8L })
        advanceUntilIdle()
        val second = requireNotNull(viewModel.uiState.value.undo)

        viewModel.undoDelete(first.token)
        advanceUntilIdle()

        assertTrue(repository.restoredTemplateIds.isEmpty())
        assertEquals(second, viewModel.uiState.value.undo)
    }

    @Test
    fun `a failed restore reports a message instead of silently dropping it`() = runTest(dispatcher) {
        repository.restoreError = IllegalStateException("UNIQUE constraint failed")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.delete(viewModel.uiState.value.rows.single())
        advanceUntilIdle()
        val undo = requireNotNull(viewModel.uiState.value.undo)
        viewModel.undoDelete(undo.token)
        advanceUntilIdle()

        assertEquals(
            LessonTimeTemplatesMessageType.RestoreFailed,
            viewModel.uiState.value.message?.type,
        )
        // 模板没恢复回来，列表仍然是空的。
        assertTrue(viewModel.uiState.value.rows.isEmpty())
    }

    /**
     * WhileSubscribed 没人订阅时上游不会启动，uiState 一直停在初始值。
     * 这里常驻收集一份，runTest 结束时由 backgroundScope 自动取消。
     */
    private fun TestScope.createViewModel(): LessonTimeTemplatesViewModel {
        val viewModel = LessonTimeTemplatesViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect { } }
        return viewModel
    }
}

/** 只保留模板列表用得到的行为：两个表各一个 StateFlow，删除会级联掉节次。 */
private class FakeTemplateRepository : ScheduleRepository {
    private val templates = MutableStateFlow<List<LessonTimeTemplateEntity>>(emptyList())
    private val items = MutableStateFlow<List<LessonTimeTemplateItemEntity>>(emptyList())

    val restoredTemplateIds = mutableListOf<Long>()

    var restoreError: Exception? = null

    fun seed(template: LessonTimeTemplateEntity, items: List<LessonTimeTemplateItemEntity>) {
        templates.value = templates.value + template
        this.items.value = this.items.value + items
    }

    fun isGone(templateId: Long): Boolean = templates.value.none { it.id == templateId }

    fun itemCount(templateId: Long): Int = items.value.count { it.templateId == templateId }

    override fun observeLessonTimeTemplates(): Flow<List<LessonTimeTemplateEntity>> = templates

    override fun observeLessonTimeTemplateItems(): Flow<List<LessonTimeTemplateItemEntity>> = items

    override suspend fun getLessonTimeTemplateByIdOnce(templateId: Long): LessonTimeTemplateEntity? =
        templates.value.firstOrNull { it.id == templateId }

    override suspend fun getLessonTimeTemplateItemsOnce(
        templateId: Long,
    ): List<LessonTimeTemplateItemEntity> = items.value.filter { it.templateId == templateId }

    override suspend fun deleteLessonTimeTemplate(template: LessonTimeTemplateEntity) {
        templates.value = templates.value.filterNot { it.id == template.id }
        items.value = items.value.filterNot { it.templateId == template.id }
    }

    override suspend fun restoreLessonTimeTemplate(
        template: LessonTimeTemplateEntity,
        items: List<LessonTimeTemplateItemEntity>,
    ) {
        restoreError?.let { throw it }
        restoredTemplateIds += template.id
        templates.value = templates.value + template
        this.items.value = this.items.value + items
    }

    override suspend fun replaceLessonTimeTemplate(
        template: LessonTimeTemplateEntity,
        items: List<LessonTimeTemplateItemEntity>,
    ) {
        templates.value = templates.value.map { if (it.id == template.id) template else it }
        this.items.value = this.items.value.filterNot { it.templateId == template.id } + items
    }

    override fun observeCurrentTimetableId(): Flow<Long?> = flowOf(null)

    override fun observeTimetable(timetableId: Long): Flow<TimetableEntity?> = flowOf(null)

    override fun observeCourses(timetableId: Long): Flow<List<CourseEntity>> = flowOf(emptyList())

    override fun observeLessonTimes(timetableId: Long): Flow<List<LessonTimeEntity>> =
        flowOf(emptyList())

    override suspend fun insertLessonTime(lessonTime: LessonTimeEntity): Long = 0L

    override suspend fun updateLessonTime(lessonTime: LessonTimeEntity) = Unit

    override suspend fun deleteLessonTime(lessonTime: LessonTimeEntity) = Unit

    override suspend fun getLessonTimeTemplateIdByName(name: String): Long? =
        templates.value.firstOrNull { it.name == name }?.id

    override suspend fun saveLessonTimeTemplateFromTimetable(
        timetableId: Long,
        name: String,
        overwrite: Boolean,
    ): Long = 0L

    override fun observeDayNotes(timetableId: Long): Flow<List<DayNoteEntity>> = flowOf(emptyList())

    override suspend fun upsertDayNote(note: DayNoteEntity): Long = 0L

    override suspend fun deleteDayNote(timetableId: Long, date: String) = Unit
}
