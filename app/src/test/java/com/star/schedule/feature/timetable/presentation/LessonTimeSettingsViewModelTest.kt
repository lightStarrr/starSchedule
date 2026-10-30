package com.star.schedule.feature.timetable.presentation

import com.star.schedule.core.database.CourseEntity
import com.star.schedule.core.database.DayNoteEntity
import com.star.schedule.core.database.LessonTimeEntity
import com.star.schedule.core.database.TimetableEntity
import com.star.schedule.feature.schedule.domain.ScheduleRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
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
 * 撤销提示的身份标识。
 *
 * 这里锁的是「倒计时期间又删了一节」这件事：两次删除可能落成同一个节次号，
 * 界面只能靠 token 区分，旧提示迟到的回调也不能把新删除的撤销数据清掉。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LessonTimeSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeScheduleRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `deleting the same period twice bumps the token and keeps the newest undo`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // 先删 10:00，剩下的会被重排，11:00 变成第 3 节。
            viewModel.delete(viewModel.uiState.value.rows[2])
            advanceUntilIdle()
            val first = requireNotNull(viewModel.uiState.value.undo)

            // 再删列表里现在的第 3 节：文案和上一次完全一样，只有 token 不同。
            viewModel.delete(viewModel.uiState.value.rows[2])
            advanceUntilIdle()
            val second = requireNotNull(viewModel.uiState.value.undo)

            assertEquals(3, first.period)
            assertEquals(3, second.period)
            assertTrue(second.token > first.token)
            assertEquals(listOf(3L, 4L), repository.deletedIds)
        }

    @Test
    fun `a late timeout of the previous undo does not clear the newest one`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val first = deleteSameRowTwice(viewModel)
        val second = requireNotNull(viewModel.uiState.value.undo)

        // 旧提示的倒计时走完，回调带着旧 token 迟到。
        viewModel.dismissUndo(first.token)
        advanceUntilIdle()

        assertEquals(second, viewModel.uiState.value.undo)
    }

    @Test
    fun `a late undo of the previous deletion restores nothing`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        val first = deleteSameRowTwice(viewModel)
        val second = requireNotNull(viewModel.uiState.value.undo)

        viewModel.undoDelete(first.token)
        advanceUntilIdle()
        assertTrue(repository.inserted.isEmpty())
        assertEquals(second, viewModel.uiState.value.undo)

        // 只有最新那次删除可以撤销：恢复的是 11:00，10:00 视为已确认删除。
        viewModel.undoDelete(second.token)
        advanceUntilIdle()
        assertEquals(listOf(4L), repository.inserted.map { it.id })
        assertNull(viewModel.uiState.value.undo)
    }

    @Test
    fun `repeated undo of the same token only inserts once`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.delete(viewModel.uiState.value.rows[2])
        advanceUntilIdle()
        val undo = requireNotNull(viewModel.uiState.value.undo)

        viewModel.undoDelete(undo.token)
        viewModel.undoDelete(undo.token)
        advanceUntilIdle()

        assertEquals(1, repository.inserted.size)
    }

    @Test
    fun `overlapping deletes are serialized in call order`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        // 卡住第一次删除的落库，制造「上一次还没写完就又删了一条」的窗口。
        val gate = CompletableDeferred<Unit>()
        repository.deleteGate = gate
        viewModel.delete(viewModel.uiState.value.rows[2])
        advanceUntilIdle()
        assertTrue(repository.deletedIds.isEmpty())

        viewModel.delete(viewModel.uiState.value.rows[3])
        advanceUntilIdle()
        // 第二条还没进 Repository：写操作是串行的。
        assertTrue(repository.deletedIds.isEmpty())

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(3L, 4L), repository.deletedIds)
        // 提示要说用户划掉那一刻看到的节次号，所以这里是 4 而不是重排后的 3。
        assertEquals(4, viewModel.uiState.value.undo?.period)
    }

    /**
     * 按线上最短路径复现「两次都是第 3 节」：删第 3 节 → 原第 4 节变成第 3 节 → 再删。
     * 返回第一次删除留下的 undo，方便后续用旧 token 验证迟到的回调。
     */
    private suspend fun TestScope.deleteSameRowTwice(
        viewModel: LessonTimeSettingsViewModel,
    ): LessonTimeUndo {
        viewModel.delete(viewModel.uiState.value.rows[2])
        advanceUntilIdle()
        val first = requireNotNull(viewModel.uiState.value.undo)
        viewModel.delete(viewModel.uiState.value.rows[2])
        advanceUntilIdle()
        return first
    }

    @Test
    fun `saving a template trims the name and reports success`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startSaveTemplate()
        viewModel.updateTemplateName("  早八作息  ")
        viewModel.confirmSaveTemplate()
        advanceUntilIdle()

        assertEquals(listOf("早八作息" to false), repository.savedTemplates)
        assertNull(viewModel.uiState.value.saveTemplate)
        assertEquals(LessonTimeMessageType.TemplateSaved, viewModel.uiState.value.message?.type)
    }

    @Test
    fun `an existing name asks for overwrite before writing`() = runTest(dispatcher) {
        repository.existingTemplateName = "早八作息"
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startSaveTemplate()
        viewModel.updateTemplateName("早八作息")
        viewModel.confirmSaveTemplate()
        advanceUntilIdle()

        // 只问不写：覆盖会丢掉旧模板，必须先确认。
        assertTrue(repository.savedTemplates.isEmpty())
        assertTrue(viewModel.uiState.value.saveTemplate?.confirmingOverwrite == true)

        viewModel.confirmOverwriteTemplate()
        advanceUntilIdle()

        assertEquals(listOf("早八作息" to true), repository.savedTemplates)
        assertNull(viewModel.uiState.value.saveTemplate)
        assertEquals(LessonTimeMessageType.TemplateOverwritten, viewModel.uiState.value.message?.type)
    }

    @Test
    fun `dismissing the overwrite prompt keeps the typed name`() = runTest(dispatcher) {
        repository.existingTemplateName = "早八作息"
        val viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.startSaveTemplate()
        viewModel.updateTemplateName("早八作息")
        viewModel.confirmSaveTemplate()
        advanceUntilIdle()

        viewModel.dismissOverwriteTemplate()
        advanceUntilIdle()

        val dialog = requireNotNull(viewModel.uiState.value.saveTemplate)
        assertTrue(!dialog.confirmingOverwrite)
        assertEquals("早八作息", dialog.name)
    }

    @Test
    fun `a blank name is rejected without writing`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startSaveTemplate()
        viewModel.updateTemplateName("   ")
        viewModel.confirmSaveTemplate()
        advanceUntilIdle()

        assertEquals(SaveTemplateError.EmptyName, viewModel.uiState.value.saveTemplate?.error)
        assertTrue(repository.savedTemplates.isEmpty())
    }

    @Test
    fun `a failed write keeps the dialog open with its error`() = runTest(dispatcher) {
        repository.saveError = IllegalStateException("disk full")
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startSaveTemplate()
        viewModel.updateTemplateName("早八作息")
        viewModel.confirmSaveTemplate()
        advanceUntilIdle()

        // 名称是用户刚敲的，失败了不要把他刚输入的对话框关掉。
        val dialog = requireNotNull(viewModel.uiState.value.saveTemplate)
        assertEquals(SaveTemplateError.Failed, dialog.error)
        assertEquals("早八作息", dialog.name)
        assertTrue(!dialog.isSaving)
    }

    @Test
    fun `no lesson times means nothing to save into a template`() = runTest(dispatcher) {
        repository.clearLessonTimes()
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startSaveTemplate()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.saveTemplate)
        assertEquals(LessonTimeMessageType.NothingToSave, viewModel.uiState.value.message?.type)
    }

    @Test
    fun `template names are limited to 100 characters`() {
        assertNull(LessonTimeSettingsViewModel.validateTemplateName("课表"))
        assertNull(LessonTimeSettingsViewModel.validateTemplateName("字".repeat(100)))
        assertEquals(
            SaveTemplateError.NameTooLong,
            LessonTimeSettingsViewModel.validateTemplateName("字".repeat(101)),
        )
    }

    /**
     * WhileSubscribed 没人订阅时上游不会启动，uiState 永远停在初始值（timetableId 为 null），
     * delete 会静默 return。这里常驻收集一份，runTest 结束时由 backgroundScope 自动取消。
     */
    private fun TestScope.createViewModel(): LessonTimeSettingsViewModel {
        val viewModel = LessonTimeSettingsViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect { } }
        return viewModel
    }
}

/**
 * 只保留测试用得到的行为。
 *
 * [resort] 复刻 `ScheduleDao.deleteLessonTimeAutoSort` 的重排规则（按开始时间重新编号 1..n），
 * 没有它造不出「两次删除都是第 3 节」这个复现条件。
 */
private class FakeScheduleRepository : ScheduleRepository {
    private val timetableId = MutableStateFlow<Long?>(1L)
    private val lessonTimes = MutableStateFlow(
        listOf(
            LessonTimeEntity(id = 1L, timetableId = 1L, period = 1, startTime = "08:00", endTime = "08:45"),
            LessonTimeEntity(id = 2L, timetableId = 1L, period = 2, startTime = "09:00", endTime = "09:45"),
            LessonTimeEntity(id = 3L, timetableId = 1L, period = 3, startTime = "10:00", endTime = "10:45"),
            LessonTimeEntity(id = 4L, timetableId = 1L, period = 4, startTime = "11:00", endTime = "11:45"),
        ),
    )

    val deletedIds = mutableListOf<Long>()
    val inserted = mutableListOf<LessonTimeEntity>()

    /** 保存模板的调用记录：名称 + 是否覆盖。 */
    val savedTemplates = mutableListOf<Pair<String, Boolean>>()

    /** 非 null 时视为库里已经有同名模板，用来触发覆盖确认。 */
    var existingTemplateName: String? = null

    /** 非 null 时写库直接失败。 */
    var saveError: Exception? = null

    /** 非 null 时删除会卡在这里，用来制造写入重叠。 */
    var deleteGate: CompletableDeferred<Unit>? = null

    override fun observeCurrentTimetableId(): Flow<Long?> = timetableId

    override fun observeTimetable(timetableId: Long): Flow<TimetableEntity?> = flowOf(null)

    override fun observeCourses(timetableId: Long): Flow<List<CourseEntity>> = flowOf(emptyList())

    override fun observeLessonTimes(timetableId: Long): Flow<List<LessonTimeEntity>> = lessonTimes

    override suspend fun insertLessonTime(lessonTime: LessonTimeEntity): Long {
        inserted += lessonTime
        resort { rows -> rows + lessonTime }
        return lessonTime.id
    }

    override suspend fun updateLessonTime(lessonTime: LessonTimeEntity) = Unit

    override suspend fun deleteLessonTime(lessonTime: LessonTimeEntity) {
        deleteGate?.await()
        deletedIds += lessonTime.id
        resort { rows -> rows.filterNot { it.id == lessonTime.id } }
    }

    override suspend fun getLessonTimeTemplateIdByName(name: String): Long? =
        if (name == existingTemplateName) TEMPLATE_ID else null

    override suspend fun saveLessonTimeTemplateFromTimetable(
        timetableId: Long,
        name: String,
        overwrite: Boolean,
    ): Long {
        saveError?.let { throw it }
        savedTemplates += name to overwrite
        return TEMPLATE_ID
    }

    fun clearLessonTimes() {
        lessonTimes.value = emptyList()
    }

    override fun observeDayNotes(timetableId: Long): Flow<List<DayNoteEntity>> = flowOf(emptyList())

    override suspend fun upsertDayNote(note: DayNoteEntity): Long = 0L

    override suspend fun deleteDayNote(timetableId: Long, date: String) = Unit

    private fun resort(transform: (List<LessonTimeEntity>) -> List<LessonTimeEntity>) {
        lessonTimes.update { rows ->
            transform(rows)
                .sortedBy { it.startTime }
                .mapIndexed { index, row -> row.copy(period = index + 1) }
        }
    }

    private companion object {
        const val TEMPLATE_ID = 1L
    }
}
