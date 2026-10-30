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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 编辑模板：草稿、改动标记与整份保存。
 *
 * 这里锁的是「编辑期间不写库」和「保存时整份替换并重排节次号」——
 * 半保存会让列表里的「共 N 节」和实际节次对不上。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LessonTimeTemplateEditViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeEditRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository.seed(
            template = LessonTimeTemplateEntity(
                id = 7L,
                name = "早八作息",
                createdAt = 1L,
                updatedAt = 100L,
            ),
            items = listOf(
                LessonTimeTemplateItemEntity(
                    id = 1L,
                    templateId = 7L,
                    period = 2,
                    startTime = "09:00",
                    endTime = "09:45",
                ),
                LessonTimeTemplateItemEntity(
                    id = 2L,
                    templateId = 7L,
                    period = 1,
                    startTime = "08:00",
                    endTime = "08:45",
                ),
            ),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loading sorts the periods by start time and renumbers them`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("早八作息", state.name)
        assertEquals(listOf("08:00", "09:00"), state.rows.map { it.startTime })
        assertEquals(listOf(1, 2), state.rows.map { it.period })
        assertFalse(state.isDirty)
    }

    @Test
    fun `a freshly loaded template is not dirty`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDirty)
        assertTrue(repository.replaced.isEmpty())
    }

    @Test
    fun `editing a period only touches the draft until saved`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startEdit(viewModel.uiState.value.rows[1])
        viewModel.setTime(LessonTimeField.Start, 9 * 60 + 30)
        viewModel.saveEditor()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDirty)
        assertTrue(repository.replaced.isEmpty())
        // 改到 09:30 后仍然排在 08:00 后面。
        assertEquals(listOf("08:00", "09:30"), viewModel.uiState.value.rows.map { it.startTime })
    }

    @Test
    fun `saving replaces the whole template and closes the page`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startRename()
        viewModel.updateRenameName("秋季作息")
        viewModel.confirmRename()
        advanceUntilIdle()

        viewModel.delete(viewModel.uiState.value.rows[0])
        advanceUntilIdle()
        viewModel.save()
        advanceUntilIdle()

        val saved = repository.replaced.single()
        assertEquals(7L, saved.template.id)
        assertEquals("秋季作息", saved.template.name)
        // 原来的 09:00 那节删掉后重新编号成第 1 节。
        assertEquals(listOf(1), saved.items.map { it.period })
        assertEquals(listOf("09:00"), saved.items.map { it.startTime })
        assertTrue(viewModel.uiState.value.finish)
    }

    @Test
    fun `renaming to an existing template name is rejected`() = runTest(dispatcher) {
        repository.takenName = "晚课"
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startRename()
        viewModel.updateRenameName("晚课")
        viewModel.confirmRename()
        advanceUntilIdle()

        assertEquals(
            RenameTemplateError.NameTaken,
            viewModel.uiState.value.rename?.error,
        )
        assertEquals("早八作息", viewModel.uiState.value.name)
    }

    @Test
    fun `leaving with unsaved changes asks first`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startRename()
        viewModel.updateRenameName("秋季作息")
        viewModel.confirmRename()
        advanceUntilIdle()

        viewModel.requestBack()
        assertTrue(viewModel.uiState.value.discardConfirm)
        assertFalse(viewModel.uiState.value.finish)

        viewModel.confirmDiscard()
        assertTrue(viewModel.uiState.value.finish)
        assertTrue(repository.replaced.isEmpty())
    }

    @Test
    fun `leaving without changes closes straight away`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestBack()

        assertFalse(viewModel.uiState.value.discardConfirm)
        assertTrue(viewModel.uiState.value.finish)
    }

    @Test
    fun `the save button asks which way to save`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.requestSave()
        assertTrue(viewModel.uiState.value.saveChoice)

        viewModel.dismissSaveChoice()
        assertFalse(viewModel.uiState.value.saveChoice)
        assertTrue(repository.replaced.isEmpty())
        assertTrue(repository.created.isEmpty())
    }

    @Test
    fun `saving as new adds a number when the name is already used`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.saveAsNew()
        advanceUntilIdle()

        // 当前模板自己占着「早八作息」，所以另存出来的是「早八作息 (1)」。
        val created = repository.created.single()
        assertEquals("早八作息 (1)", created.first)
        assertEquals(listOf(1, 2), created.second.map { it.period })
        assertTrue(viewModel.uiState.value.finish)
        assertTrue(repository.replaced.isEmpty())
    }

    @Test
    fun `saving as new keeps a free name as is`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.startRename()
        viewModel.updateRenameName("秋季作息")
        viewModel.confirmRename()
        advanceUntilIdle()

        viewModel.saveAsNew()
        advanceUntilIdle()

        assertEquals("秋季作息", repository.created.single().first)
    }

    @Test
    fun `a deleted template shows as missing`() = runTest(dispatcher) {
        repository.remove(7L)
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isMissing)
        assertNull(viewModel.uiState.value.rows.firstOrNull())
    }

    @Test
    fun `a taken name counts up until a free one`() = runTest(dispatcher) {
        val taken = setOf("早八作息", "早八作息 (1)", "早八作息 (2)")

        val name = resolveNewTemplateName("早八作息") { it in taken }

        assertEquals("早八作息 (3)", name)
    }

    @Test
    fun `a numbered name stays within the length limit`() = runTest(dispatcher) {
        val base = "字".repeat(LessonTimeSettingsViewModel.MAX_TEMPLATE_NAME_LENGTH)

        val name = resolveNewTemplateName(base) { it == base }

        assertEquals(LessonTimeSettingsViewModel.MAX_TEMPLATE_NAME_LENGTH, name.length)
        assertTrue(name.endsWith(" (1)"))
    }

    private fun createViewModel(): LessonTimeTemplateEditViewModel =
        LessonTimeTemplateEditViewModel(repository, templateId = 7L)
}

/** 只保留编辑页用得到的行为：读一份模板、记一次整份替换。 */
private class FakeEditRepository : ScheduleRepository {
    private var template: LessonTimeTemplateEntity? = null
    private var items: List<LessonTimeTemplateItemEntity> = emptyList()

    /** 测试里「已经被别的模板占用」的名字。 */
    var takenName: String? = null

    val replaced = mutableListOf<SavedTemplate>()

    /** 「另存为新模板」的调用记录：名称 + 节次。 */
    val created = mutableListOf<Pair<String, List<LessonTimeTemplateItemEntity>>>()

    data class SavedTemplate(
        val template: LessonTimeTemplateEntity,
        val items: List<LessonTimeTemplateItemEntity>,
    )

    fun seed(template: LessonTimeTemplateEntity, items: List<LessonTimeTemplateItemEntity>) {
        this.template = template
        this.items = items
    }

    fun remove(templateId: Long) {
        if (template?.id == templateId) {
            template = null
            items = emptyList()
        }
    }

    override suspend fun getLessonTimeTemplateByIdOnce(templateId: Long): LessonTimeTemplateEntity? =
        template?.takeIf { it.id == templateId }

    override suspend fun getLessonTimeTemplateItemsOnce(
        templateId: Long,
    ): List<LessonTimeTemplateItemEntity> = items.filter { it.templateId == templateId }

    /** 库里的真实占用情况：当前模板自己占着原名，另存过的名字也算占用。 */
    override suspend fun getLessonTimeTemplateIdByName(name: String): Long? = when {
        name == takenName -> 99L
        name == template?.name -> template?.id
        created.any { it.first == name } -> 100L
        else -> null
    }

    override suspend fun replaceLessonTimeTemplate(
        template: LessonTimeTemplateEntity,
        items: List<LessonTimeTemplateItemEntity>,
    ) {
        replaced += SavedTemplate(template, items)
        this.template = template
        this.items = items
    }

    override fun observeLessonTimeTemplates(): Flow<List<LessonTimeTemplateEntity>> =
        flowOf(listOfNotNull(template))

    override fun observeLessonTimeTemplateItems(): Flow<List<LessonTimeTemplateItemEntity>> =
        flowOf(items)

    override suspend fun deleteLessonTimeTemplate(template: LessonTimeTemplateEntity) = Unit

    override suspend fun restoreLessonTimeTemplate(
        template: LessonTimeTemplateEntity,
        items: List<LessonTimeTemplateItemEntity>,
    ) = Unit

    override suspend fun saveLessonTimeTemplateFromItems(
        templateName: String,
        lessonTimes: List<LessonTimeTemplateItemEntity>,
        overwrite: Boolean,
    ): Long {
        created += templateName to lessonTimes
        return 100L
    }

    override suspend fun saveLessonTimeTemplateFromTimetable(
        timetableId: Long,
        name: String,
        overwrite: Boolean,
    ): Long = 0L

    override fun observeCurrentTimetableId(): Flow<Long?> = flowOf(null)

    override fun observeTimetable(timetableId: Long): Flow<TimetableEntity?> = flowOf(null)

    override fun observeCourses(timetableId: Long): Flow<List<CourseEntity>> = flowOf(emptyList())

    override fun observeLessonTimes(timetableId: Long): Flow<List<LessonTimeEntity>> =
        flowOf(emptyList())

    override suspend fun insertLessonTime(lessonTime: LessonTimeEntity): Long = 0L

    override suspend fun updateLessonTime(lessonTime: LessonTimeEntity) = Unit

    override suspend fun deleteLessonTime(lessonTime: LessonTimeEntity) = Unit

    override fun observeDayNotes(timetableId: Long): Flow<List<DayNoteEntity>> = flowOf(emptyList())

    override suspend fun upsertDayNote(note: DayNoteEntity): Long = 0L

    override suspend fun deleteDayNote(timetableId: Long, date: String) = Unit
}
