package com.star.schedule.feature.timetable.presentation

import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.DayNoteEntity
import com.star.schedule.core.database.LessonTimeApplySnapshot
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
            val undo = requireNotNull(viewModel.uiState.value.undo) as LessonTimeTemplatesUndo.Deleted
            assertEquals("早八作息", undo.name)

            viewModel.undo(undo.token)
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

        viewModel.undo(first.token)
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
        viewModel.undo(undo.token)
        advanceUntilIdle()

        assertEquals(
            LessonTimeTemplatesMessageType.RestoreFailed,
            viewModel.uiState.value.message?.type,
        )
        // 模板没恢复回来，列表仍然是空的。
        assertTrue(viewModel.uiState.value.rows.isEmpty())
    }


    @Test
    fun `requesting apply previews the courses that would lose their time`() = runTest(dispatcher) {
        repository.seedCourses(listOf(course("晚自习", listOf(5, 6))))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestApply(viewModel.uiState.value.rows.single())
        advanceUntilIdle()

        val preview = requireNotNull(viewModel.uiState.value.applyPreview)
        assertEquals("早八作息", preview.templateName)
        assertEquals("2026 秋季学期", preview.timetableName)
        assertEquals(2, preview.periodCount)
        assertEquals(listOf("晚自习"), preview.missing.map { it.courseName })
        assertEquals(listOf(5, 6), preview.missing.single().periods)
    }

    @Test
    fun `confirming apply writes into the current timetable and offers undo`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestApply(viewModel.uiState.value.rows.single())
        advanceUntilIdle()
        viewModel.confirmApply()
        advanceUntilIdle()

        assertEquals(listOf(5L to 7L), repository.applied)
        assertNull(viewModel.uiState.value.applyPreview)
        val undo = requireNotNull(viewModel.uiState.value.undo) as LessonTimeTemplatesUndo.Applied
        assertEquals("早八作息", undo.templateName)
    }

    @Test
    fun `an empty template is not applied`() = runTest(dispatcher) {
        repository.seed(
            template = LessonTimeTemplateEntity(id = 9L, name = "空模板", createdAt = 3L, updatedAt = 3L),
            items = emptyList(),
        )
        val viewModel = createViewModel()
        advanceUntilIdle()

        val emptyRow = viewModel.uiState.value.rows.first { it.id == 9L }
        viewModel.requestApply(emptyRow)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.applyPreview)
        assertEquals(
            LessonTimeTemplatesMessageType.TemplateEmpty,
            viewModel.uiState.value.message?.type,
        )
        assertTrue(repository.applied.isEmpty())
    }

    @Test
    fun `without a current timetable applying is unavailable`() = runTest(dispatcher) {
        repository.clearTimetable()
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(!viewModel.uiState.value.canApply)
        viewModel.requestApply(viewModel.uiState.value.rows.single())
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.applyPreview)
        assertEquals(
            LessonTimeTemplatesMessageType.NoTimetable,
            viewModel.uiState.value.message?.type,
        )
    }

    @Test
    fun `a failed apply reports a message and leaves no undo`() = runTest(dispatcher) {
        repository.applyError = IllegalStateException("disk full")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestApply(viewModel.uiState.value.rows.single())
        advanceUntilIdle()
        viewModel.confirmApply()
        advanceUntilIdle()

        assertEquals(
            LessonTimeTemplatesMessageType.ApplyFailed,
            viewModel.uiState.value.message?.type,
        )
        assertNull(viewModel.uiState.value.undo)
    }

    @Test
    fun `undoing an apply restores the snapshot it got`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestApply(viewModel.uiState.value.rows.single())
        advanceUntilIdle()
        viewModel.confirmApply()
        advanceUntilIdle()
        val undo = requireNotNull(viewModel.uiState.value.undo)

        viewModel.undo(undo.token)
        advanceUntilIdle()

        assertEquals(listOf(5L), repository.restoredSnapshots.map { it.timetableId })
        assertNull(viewModel.uiState.value.undo)
    }

    @Test
    fun `a failed undo of an apply reports its own message`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestApply(viewModel.uiState.value.rows.single())
        advanceUntilIdle()
        viewModel.confirmApply()
        advanceUntilIdle()
        repository.restoreError = IllegalStateException("disk full")
        val undo = requireNotNull(viewModel.uiState.value.undo)

        viewModel.undo(undo.token)
        advanceUntilIdle()

        assertEquals(
            LessonTimeTemplatesMessageType.ApplyUndoFailed,
            viewModel.uiState.value.message?.type,
        )
    }

    @Test
    fun `a late undo of a previous apply restores nothing`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val row = viewModel.uiState.value.rows.single()

        viewModel.requestApply(row)
        advanceUntilIdle()
        viewModel.confirmApply()
        advanceUntilIdle()
        val first = requireNotNull(viewModel.uiState.value.undo)

        viewModel.requestApply(row)
        advanceUntilIdle()
        viewModel.confirmApply()
        advanceUntilIdle()
        val second = requireNotNull(viewModel.uiState.value.undo)

        viewModel.undo(first.token)
        advanceUntilIdle()

        assertTrue(repository.restoredSnapshots.isEmpty())
        assertEquals(second, viewModel.uiState.value.undo)
    }

    private fun course(name: String, periods: List<Int>) = CourseEntity(
        id = 0,
        timetableId = 5,
        name = name,
        teacher = "",
        location = "",
        dayOfWeek = 1,
        periods = periods,
        weeks = listOf(1),
    )

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
    private val timetableId = MutableStateFlow<Long?>(5L)
    private val timetable = MutableStateFlow<TimetableEntity?>(
        TimetableEntity(id = 5L, name = "2026 秋季学期", showWeekend = false, startDate = "2026-09-01"),
    )
    private val courses = MutableStateFlow<List<CourseEntity>>(emptyList())

    val restoredTemplateIds = mutableListOf<Long>()

    /** 「应用」的调用记录：timetableId to templateId。 */
    val applied = mutableListOf<Pair<Long, Long>>()

    /** 撤销时收到的快照。 */
    val restoredSnapshots = mutableListOf<LessonTimeApplySnapshot>()

    var restoreError: Exception? = null

    var applyError: Exception? = null

    fun clearTimetable() {
        timetableId.value = null
        timetable.value = null
    }

    fun seedCourses(list: List<CourseEntity>) {
        courses.value = list
    }

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

    override suspend fun applyLessonTimeTemplateToTimetable(
        timetableId: Long,
        templateId: Long,
    ): LessonTimeApplySnapshot? {
        applyError?.let { throw it }
        // 空模板不写库，和真实实现一致。
        if (items.value.none { it.templateId == templateId }) return null
        applied += timetableId to templateId
        return LessonTimeApplySnapshot(timetableId = timetableId, lessonTimesBefore = emptyList())
    }

    override suspend fun restoreAppliedLessonTimeTemplate(snapshot: LessonTimeApplySnapshot) {
        restoredSnapshots += snapshot
        restoreError?.let { throw it }
    }

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

    override fun observeCurrentTimetableId(): Flow<Long?> = timetableId

    override fun observeTimetable(timetableId: Long): Flow<TimetableEntity?> = timetable

    override fun observeCourses(timetableId: Long): Flow<List<CourseEntity>> = courses

    override fun observeLessonTimes(timetableId: Long): Flow<List<LessonTimeEntity>> =
        flowOf(emptyList())

    override suspend fun insertLessonTime(lessonTime: LessonTimeEntity): Long = 0L

    override suspend fun updateLessonTime(lessonTime: LessonTimeEntity) = Unit

    override suspend fun deleteLessonTime(lessonTime: LessonTimeEntity) = Unit

    override suspend fun getLessonTimeTemplateIdByName(name: String): Long? =
        templates.value.firstOrNull { it.name == name }?.id

    override suspend fun saveLessonTimeTemplateFromItems(
        templateName: String,
        lessonTimes: List<LessonTimeTemplateItemEntity>,
        overwrite: Boolean,
    ): Long = 0L

    override suspend fun saveLessonTimeTemplateFromTimetable(
        timetableId: Long,
        name: String,
        overwrite: Boolean,
    ): Long = 0L

    override fun observeDayNotes(timetableId: Long): Flow<List<DayNoteEntity>> = flowOf(emptyList())

    override suspend fun upsertDayNote(note: DayNoteEntity): Long = 0L

    override suspend fun deleteDayNote(timetableId: Long, date: String) = Unit
}
