package com.star.schedule.feature.timetable.presentation

import com.star.schedule.core.database.entity.CourseEntity
import com.star.schedule.core.database.entity.CourseSessionEntity
import com.star.schedule.core.database.entity.CourseWithSessions
import com.star.schedule.core.database.entity.LessonTimeEntity
import com.star.schedule.core.database.entity.LessonTimeTemplateEntity
import com.star.schedule.core.database.entity.LessonTimeTemplateItemEntity
import com.star.schedule.core.database.entity.TimetableEntity
import com.star.schedule.feature.timetable.domain.CourseRules.CourseNameError
import com.star.schedule.feature.timetable.domain.TimetableRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CourseSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeTimetableRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `groups courses with sessions filters by teacher and renames with validation`() = runTest(dispatcher) {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val initial = viewModel.uiState.value
        assertTrue(initial.hasLessonTimes)
        assertEquals(2, initial.totalCourseCount)
        assertEquals(2, initial.groups.size)

        // 按老师过滤仅保留高等数学
        viewModel.toggleSearch()
        viewModel.updateSearchQuery("张老师")
        advanceUntilIdle()
        val filtered = viewModel.uiState.value
        assertTrue(filtered.searchActive)
        assertEquals(1, filtered.groups.size)
        assertEquals("高等数学", filtered.groups.single().name)
        assertEquals(2, filtered.groups.single().sessions.size)

        // 退出搜索
        viewModel.toggleSearch()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.searchActive)
        assertEquals(2, viewModel.uiState.value.groups.size)

        // 重命名课程：校验重名错误，随后改为新名称
        val mathCourse = repository.coursesFlow.value.first { it.course.id == 1L }.course
        viewModel.startRenameCourse(mathCourse)
        viewModel.updateRenameCourseName("大学物理")
        viewModel.confirmRenameCourse()
        advanceUntilIdle()
        assertEquals(CourseNameError.Duplicate, viewModel.uiState.value.renamingCourse?.error)

        viewModel.updateRenameCourseName("高等数学 A")
        viewModel.confirmRenameCourse()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.renamingCourse)
        assertTrue(repository.coursesFlow.value.any { it.course.id == 1L && it.course.name == "高等数学 A" })
    }

    @Test
    fun `deleting last session keeps empty course and undo restores both session and whole course`() =
        runTest(dispatcher) {
            val viewModel = createViewModel()
            advanceUntilIdle()

            // 删除大学物理唯一的时段：课程主记录仍保留，呈现 0 个时段
            val physicsGroup = viewModel.uiState.value.groups.first { it.course.id == 2L }
            val physicsSession = physicsGroup.sessions.single().session
            viewModel.deleteSession(physicsGroup.course, physicsSession)
            advanceUntilIdle()

            assertEquals(2, viewModel.uiState.value.totalCourseCount)
            val emptyPhysics = viewModel.uiState.value.groups.first { it.course.id == 2L }
            assertTrue(emptyPhysics.sessions.isEmpty())
            val sessionUndo = viewModel.uiState.value.undo as CourseUndoState.DeletedSession
            assertEquals("大学物理", sessionUndo.courseName)

            // 撤销单时段删除
            viewModel.undoDelete(sessionUndo.token)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.undo)
            assertEquals(1, viewModel.uiState.value.groups.first { it.course.id == 2L }.sessions.size)

            // 删除整门课（高等数学 + 2 个时段）后可整体恢复
            val mathGroup = viewModel.uiState.value.groups.first { it.course.id == 1L }
            viewModel.requestDeleteCourse(mathGroup)
            advanceUntilIdle()
            assertEquals("高等数学", viewModel.uiState.value.deletingCourse?.course?.name)
            viewModel.confirmDeleteCourse()
            advanceUntilIdle()

            assertEquals(1, viewModel.uiState.value.totalCourseCount)
            val wholeUndo = viewModel.uiState.value.undo as CourseUndoState.DeletedCourse
            assertEquals("高等数学", wholeUndo.courseName)

            viewModel.undoDelete(wholeUndo.token)
            advanceUntilIdle()
            assertEquals(2, viewModel.uiState.value.totalCourseCount)
            val restoredMath = viewModel.uiState.value.groups.first { it.name == "高等数学" }
            assertEquals(2, restoredMath.sessions.size)
        }

    private fun TestScope.createViewModel(): CourseSettingsViewModel {
        val viewModel = CourseSettingsViewModel(repository, timetableId = 1L)
        backgroundScope.launch {
            viewModel.uiState.collect {}
        }
        return viewModel
    }

    private class FakeTimetableRepository : TimetableRepository {
        private var nextCourseId = 10L
        private var nextSessionId = 200L

        private val lessonTimesFlow = MutableStateFlow(
            listOf(
                LessonTimeEntity(1L, 1L, 1, "08:00", "08:45"),
                LessonTimeEntity(2L, 1L, 2, "08:55", "09:40"),
                LessonTimeEntity(3L, 1L, 3, "10:00", "10:45"),
                LessonTimeEntity(4L, 1L, 4, "10:55", "11:40"),
            ),
        )

        val coursesFlow = MutableStateFlow(
            listOf(
                CourseWithSessions(
                    course = CourseEntity(id = 1L, timetableId = 1L, name = "高等数学"),
                    sessions = listOf(
                        CourseSessionEntity(
                            id = 101L,
                            courseId = 1L,
                            teacher = "张老师",
                            location = "教A-101",
                            dayOfWeek = 1,
                            periods = listOf(1, 2),
                            weeks = (1..16).toList(),
                        ),
                        CourseSessionEntity(
                            id = 102L,
                            courseId = 1L,
                            teacher = "张老师",
                            location = "教A-202",
                            dayOfWeek = 3,
                            periods = listOf(3, 4),
                            weeks = (1..16).toList(),
                        ),
                    ),
                ),
                CourseWithSessions(
                    course = CourseEntity(id = 2L, timetableId = 1L, name = "大学物理"),
                    sessions = listOf(
                        CourseSessionEntity(
                            id = 103L,
                            courseId = 2L,
                            teacher = "李老师",
                            location = "教B-301",
                            dayOfWeek = 2,
                            periods = listOf(1, 2),
                            weeks = (1..12).toList(),
                        ),
                    ),
                ),
            ),
        )

        override fun observeLessonTimes(timetableId: Long): Flow<List<LessonTimeEntity>> = lessonTimesFlow

        override fun observeCoursesWithSessions(timetableId: Long): Flow<List<CourseWithSessions>> = coursesFlow

        override suspend fun updateCourseWithReminders(course: CourseEntity) {
            coursesFlow.update { list ->
                list.map { item ->
                    if (item.course.id == course.id) item.copy(course = course) else item
                }
            }
        }

        override suspend fun deleteCourseWithReminders(course: CourseEntity) {
            coursesFlow.update { list -> list.filterNot { it.course.id == course.id } }
        }

        override suspend fun restoreCourseWithSessionsWithReminders(
            course: CourseEntity,
            sessions: List<CourseSessionEntity>,
        ): Long {
            val newCourseId = ++nextCourseId
            val restoredCourse = course.copy(id = newCourseId)
            val restoredSessions = sessions.map { it.copy(id = ++nextSessionId, courseId = newCourseId) }
            coursesFlow.update { it + CourseWithSessions(restoredCourse, restoredSessions) }
            return newCourseId
        }

        override suspend fun insertCourseSessionWithReminders(
            timetableId: Long,
            session: CourseSessionEntity,
        ): Long {
            val newSessionId = ++nextSessionId
            val inserted = session.copy(id = newSessionId)
            coursesFlow.update { list ->
                list.map { item ->
                    if (item.course.id == session.courseId) {
                        item.copy(sessions = item.sessions + inserted)
                    } else {
                        item
                    }
                }
            }
            return newSessionId
        }

        override suspend fun updateCourseSessionWithReminders(
            timetableId: Long,
            session: CourseSessionEntity,
        ) = Unit

        override suspend fun deleteCourseSessionWithReminders(
            timetableId: Long,
            session: CourseSessionEntity,
        ) {
            coursesFlow.update { list ->
                list.map { item ->
                    item.copy(sessions = item.sessions.filterNot { it.id == session.id })
                }
            }
        }

        override suspend fun upsertCourseAndInsertSessionWithReminders(
            timetableId: Long,
            courseName: String,
            session: CourseSessionEntity,
        ): Pair<Long, Long> = 0L to 0L

        override suspend fun replaceCoursesForTimetable(
            timetableId: Long,
            courses: List<Pair<CourseEntity, List<CourseSessionEntity>>>,
        ) = Unit

        override fun observeTimetables(): Flow<List<TimetableEntity>> = flowOf(emptyList())
        override fun observeTimetable(timetableId: Long): Flow<TimetableEntity?> = flowOf(null)
        override suspend fun getAllTimetablesOnce(): List<TimetableEntity> = emptyList()
        override suspend fun insertTimetableWithReminders(timetable: TimetableEntity): Long = 0L
        override suspend fun updateTimetableWithReminders(timetable: TimetableEntity) = Unit
        override suspend fun deleteTimetableWithReminders(timetable: TimetableEntity) = Unit
        override suspend fun insertOrUpdateLessonTimeAutoSort(lessonTime: LessonTimeEntity, isInsert: Boolean): Long = 0L
        override suspend fun deleteLessonTimeAutoSort(lessonTime: LessonTimeEntity) = Unit
        override fun observeLessonTimeTemplates(): Flow<List<LessonTimeTemplateEntity>> = flowOf(emptyList())
        override suspend fun getLessonTimeTemplateByNameOnce(name: String): LessonTimeTemplateEntity? = null
        override suspend fun getLessonTimeTemplateItemsOnce(templateId: Long): List<LessonTimeTemplateItemEntity> = emptyList()
        override suspend fun saveLessonTimeTemplateFromItems(
            templateName: String,
            lessonTimes: List<LessonTimeTemplateItemEntity>,
            overwrite: Boolean,
            createdAt: Long,
            updatedAt: Long,
        ): Long = 0L
        override suspend fun saveLessonTimeTemplateFromTimetable(timetableId: Long, templateName: String, overwrite: Boolean): Long = 0L
        override suspend fun deleteLessonTimeTemplate(template: LessonTimeTemplateEntity) = Unit
    }
}
