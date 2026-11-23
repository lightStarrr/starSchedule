package com.star.schedule.feature.schedule.presentation

import com.star.schedule.core.database.entity.CourseEntity
import com.star.schedule.core.database.entity.DayNoteEntity
import com.star.schedule.core.database.LessonTimeApplySnapshot
import com.star.schedule.core.database.entity.LessonTimeEntity
import com.star.schedule.core.database.entity.LessonTimeTemplateEntity
import com.star.schedule.core.database.entity.LessonTimeTemplateItemEntity
import com.star.schedule.core.database.entity.TimetableEntity
import com.star.schedule.feature.schedule.domain.ScheduleRepository
import com.star.schedule.feature.wallpaper.domain.RecentWallpaper
import com.star.schedule.feature.wallpaper.domain.StagedWallpaper
import com.star.schedule.feature.wallpaper.domain.WallpaperBlurSpec
import com.star.schedule.feature.wallpaper.domain.WallpaperDesign
import com.star.schedule.feature.wallpaper.domain.WallpaperRepository
import com.star.schedule.feature.wallpaper.domain.WallpaperState
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleHomeViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `empty timetable selection resolves to non-loading empty state`() = runTest {
        val scheduleRepo = FakeScheduleRepository(currentTimetableId = null)
        val viewModel = ScheduleHomeViewModel(
            scheduleRepository = scheduleRepo,
            wallpaperRepository = FakeWallpaperRepository(),
            todayProvider = { LocalDate.of(2026, 9, 14) },
        )

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasTimetable)
        assertEquals(1, state.currentWeek)
        job.cancel()
    }

    @Test
    fun `week navigation clamps at week 1 and resets when switching timetable`() = runTest {
        val scheduleRepo = FakeScheduleRepository(
            currentTimetableId = 1L,
            timetables = mapOf(
                1L to TimetableEntity(
                    id = 1L,
                    name = "A",
                    showWeekend = true,
                    startDate = "2026-09-07",
                    rowHeight = 64,
                ),
                2L to TimetableEntity(
                    id = 2L,
                    name = "B",
                    showWeekend = false,
                    startDate = "2026-08-31",
                    rowHeight = 56,
                ),
            ),
            courses = mapOf(
                1L to listOf(
                    CourseEntity(
                        id = 10L,
                        timetableId = 1L,
                        name = "Math",
                        location = "A101",
                        teacher = "",
                        dayOfWeek = 6,
                        periods = listOf(1, 2),
                        weeks = listOf(1, 2),
                    ),
                ),
            ),
        )
        val viewModel = ScheduleHomeViewModel(
            scheduleRepository = scheduleRepo,
            wallpaperRepository = FakeWallpaperRepository(),
            todayProvider = { LocalDate.of(2026, 9, 14) },
        )

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        // 2026-09-07 到 2026-09-14 为第 2 周，含周六课程则 showWeekend = true
        assertEquals(2, viewModel.uiState.value.currentWeek)
        assertTrue(viewModel.uiState.value.showWeekend)
        assertEquals(64, viewModel.uiState.value.rowHeightDp)

        viewModel.previousWeek()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.currentWeek)

        // 不允许低于第 1 周
        viewModel.previousWeek()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.currentWeek)

        viewModel.nextWeek()
        viewModel.nextWeek()
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.currentWeek)

        // 切到课表 2（开学日 2026-08-31），应自动重置到课表 2 的当前周（第 3 周）且无周末课
        scheduleRepo.currentId.value = 2L
        advanceUntilIdle()
        assertEquals(2L, viewModel.uiState.value.timetableId)
        assertEquals(3, viewModel.uiState.value.currentWeek)
        assertFalse(viewModel.uiState.value.showWeekend)
        assertEquals(56, viewModel.uiState.value.rowHeightDp)
        job.cancel()
    }
}

private class FakeScheduleRepository(
    currentTimetableId: Long?,
    private val timetables: Map<Long, TimetableEntity> = emptyMap(),
    private val courses: Map<Long, List<CourseEntity>> = emptyMap(),
    private val lessonTimes: Map<Long, List<LessonTimeEntity>> = emptyMap(),
) : ScheduleRepository {
    val currentId = MutableStateFlow(currentTimetableId)

    override fun observeCurrentTimetableId(): Flow<Long?> = currentId

    override fun observeTimetable(timetableId: Long): Flow<TimetableEntity?> =
        flowOf(timetables[timetableId])

    override fun observeCourses(timetableId: Long): Flow<List<CourseEntity>> =
        flowOf(courses[timetableId].orEmpty())

    override fun observeLessonTimes(timetableId: Long): Flow<List<LessonTimeEntity>> =
        flowOf(lessonTimes[timetableId].orEmpty())

    override suspend fun insertLessonTime(lessonTime: LessonTimeEntity): Long = 0L
    override suspend fun updateLessonTime(lessonTime: LessonTimeEntity) = Unit
    override suspend fun deleteLessonTime(lessonTime: LessonTimeEntity) = Unit
    override suspend fun getLessonTimeTemplateIdByName(name: String): Long? = null
    override suspend fun saveLessonTimeTemplateFromTimetable(
        timetableId: Long,
        name: String,
        overwrite: Boolean,
    ): Long = 0L

    override fun observeLessonTimeTemplates(): Flow<List<LessonTimeTemplateEntity>> = emptyFlow()
    override fun observeLessonTimeTemplateItems(): Flow<List<LessonTimeTemplateItemEntity>> = emptyFlow()
    override suspend fun getLessonTimeTemplateByIdOnce(templateId: Long): LessonTimeTemplateEntity? = null
    override suspend fun getLessonTimeTemplateItemsOnce(templateId: Long): List<LessonTimeTemplateItemEntity> =
        emptyList()

    override suspend fun saveLessonTimeTemplateFromItems(
        templateName: String,
        lessonTimes: List<LessonTimeTemplateItemEntity>,
        overwrite: Boolean,
    ): Long = 0L

    override suspend fun replaceLessonTimeTemplate(
        template: LessonTimeTemplateEntity,
        items: List<LessonTimeTemplateItemEntity>,
    ) = Unit

    override suspend fun deleteLessonTimeTemplate(template: LessonTimeTemplateEntity) = Unit
    override suspend fun applyLessonTimeTemplateToTimetable(
        timetableId: Long,
        templateId: Long,
    ): LessonTimeApplySnapshot? = null

    override suspend fun restoreAppliedLessonTimeTemplate(snapshot: LessonTimeApplySnapshot) = Unit
    override suspend fun restoreLessonTimeTemplate(
        template: LessonTimeTemplateEntity,
        items: List<LessonTimeTemplateItemEntity>,
    ) = Unit

    override fun observeDayNotes(timetableId: Long): Flow<List<DayNoteEntity>> = emptyFlow()
    override suspend fun upsertDayNote(note: DayNoteEntity): Long = 0L
    override suspend fun deleteDayNote(timetableId: Long, date: String) = Unit
}

private class FakeWallpaperRepository : WallpaperRepository {
    override fun observe(timetableId: Long): Flow<WallpaperState> = flowOf(WallpaperState.None)
    override fun observeRecents(): Flow<List<RecentWallpaper>> = emptyFlow()
    override suspend fun importFromUri(timetableId: Long, uriString: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun stagePreview(uriString: String): Result<StagedWallpaper> =
        Result.failure(UnsupportedOperationException())

    override fun discardStaged(path: String) = Unit

    override suspend fun applyStaged(
        timetableId: Long,
        stagedPath: String,
        blur: WallpaperBlurSpec?,
        scrimEnabled: Boolean,
    ): Result<Unit> = Result.success(Unit)

    override suspend fun applyRecent(timetableId: Long, recent: RecentWallpaper): Result<Unit> =
        Result.success(Unit)

    override suspend fun applyDesign(timetableId: Long, design: WallpaperDesign): Result<Unit> =
        Result.success(Unit)

    override suspend fun clear(timetableId: Long) = Unit
}
