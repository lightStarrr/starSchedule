package com.star.schedule.feature.timetable.presentation

import com.star.schedule.core.database.entity.CourseEntity
import com.star.schedule.core.database.entity.CourseSessionEntity
import com.star.schedule.core.database.entity.CourseWithSessions
import com.star.schedule.core.database.entity.LessonTimeEntity
import com.star.schedule.core.database.entity.LessonTimeTemplateEntity
import com.star.schedule.core.database.entity.LessonTimeTemplateItemEntity
import com.star.schedule.core.database.entity.TimetableEntity
import com.star.schedule.feature.timetable.domain.CourseRules.CourseValidationError
import com.star.schedule.feature.timetable.domain.TimetableRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
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

@OptIn(ExperimentalCoroutinesApi::class)
class CourseEditViewModelTest {
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
    fun `new course creates master course and supports save and add another session`() = runTest(dispatcher) {
        val viewModel = CourseEditViewModel(repository, timetableId = 1L)
        advanceUntilIdle()

        val initial = viewModel.uiState.value
        assertFalse(initial.isLoading)
        assertTrue(initial.isNewSession)
        assertFalse(initial.showCourseSelectorMode)
        assertFalse(initial.hasChanges)
        assertEquals(setOf(1, 2), initial.selectedPeriods)
        assertEquals((1..16).toSet(), initial.selectedWeeks)

        // 名称为空时校验失败
        viewModel.save(addAnother = false)
        assertEquals(CourseValidationError.EmptyName, viewModel.uiState.value.validationError)
        assertEquals(1, repository.coursesFlow.value.size)

        // 填写新课程并触发冲突检测
        viewModel.updateName("线性代数")
        viewModel.updateTeacher("王老师")
        viewModel.updateLocation("教C-105")
        viewModel.selectDayOfWeek(2)
        viewModel.selectOddWeeks()
        assertTrue(viewModel.uiState.value.hasChanges)
        assertEquals(1, viewModel.uiState.value.conflicts.size)
        assertEquals("数据结构", viewModel.uiState.value.conflicts.single().courseName)

        // 保存并加时段：新建主表课程 + 第一条时段，切换为锁定该课程的加时段状态
        viewModel.save(addAnother = true)
        advanceUntilIdle()
        val afterFirstSave = viewModel.uiState.value
        assertFalse(afterFirstSave.finish)
        assertFalse(afterFirstSave.hasChanges)
        assertTrue(afterFirstSave.showCourseSelectorMode)
        assertEquals("线性代数", afterFirstSave.name)
        assertTrue(afterFirstSave.selectedPeriods.isEmpty())
        assertEquals(CourseEditMessageType.SavedAddNext, afterFirstSave.message?.type)
        assertEquals(2, repository.coursesFlow.value.size)

        // 再选周四 3-4 节保存到同一门课程下
        viewModel.selectDayOfWeek(4)
        viewModel.updateSelectedPeriods(setOf(3, 4))
        viewModel.save(addAnother = false)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.finish)
        val linearAlgebra = repository.coursesFlow.value.first { it.course.name == "线性代数" }
        assertEquals(2, linearAlgebra.sessions.size)
    }

    @Test
    fun `adding session for existing course pre-fills defaults and editing session keeps parent course on delete`() =
        runTest(dispatcher) {
            // 为已有课程（courseId = 1）添加时段
            val addSessionVm = CourseEditViewModel(repository, timetableId = 1L, courseId = 1L)
            advanceUntilIdle()
            val addState = addSessionVm.uiState.value
            assertTrue(addState.isNewSession)
            assertTrue(addState.showCourseSelectorMode)
            assertEquals("数据结构", addState.name)
            assertEquals("赵老师", addState.teacher)
            assertEquals("实验楼-401", addState.location)
            assertEquals((1..22).toSet(), addState.selectedWeeks)

            // 编辑已有时段（sessionId = 10）
            val editVm = CourseEditViewModel(repository, timetableId = 1L, courseId = 1L, sessionId = 10L)
            advanceUntilIdle()

            val loaded = editVm.uiState.value
            assertFalse(loaded.isNewSession)
            assertTrue(loaded.showCourseSelectorMode)
            assertEquals("数据结构", loaded.name)
            assertTrue(loaded.showExtendedWeeks)

            // 修改地点后按返回触发放弃确认
            editVm.updateLocation("实验楼-502")
            assertTrue(editVm.uiState.value.hasChanges)
            editVm.requestBack()
            assertTrue(editVm.uiState.value.showDiscardConfirm)
            editVm.dismissDiscard()
            assertFalse(editVm.uiState.value.showDiscardConfirm)

            // 删除单时段只清空 sessions，保留主表课程实体
            editVm.requestDelete()
            assertTrue(editVm.uiState.value.showDeleteConfirm)
            editVm.confirmDelete()
            advanceUntilIdle()
            assertTrue(editVm.uiState.value.finish)
            assertEquals(1, repository.coursesFlow.value.size)
            assertTrue(repository.coursesFlow.value.single().sessions.isEmpty())
        }

    @Test
    fun `missing session sets missing flag and closes cleanly on back`() = runTest(dispatcher) {
        val viewModel = CourseEditViewModel(repository, timetableId = 1L, courseId = 1L, sessionId = 999L)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isMissing)
        assertFalse(viewModel.uiState.value.missingIsCourse)
        assertNull(viewModel.uiState.value.validationError)
        viewModel.requestBack()
        assertTrue(viewModel.uiState.value.finish)
    }

    @Test
    fun `canSave tracks form validity and unsaved changes`() = runTest(dispatcher) {
        // 新建课程：初始空课名不可保存，填名后可保存；清空节次/周次或字段超长后不可保存
        val newVm = CourseEditViewModel(repository, timetableId = 1L)
        advanceUntilIdle()
        assertFalse(newVm.uiState.value.canSave)

        newVm.updateName("大学物理")
        assertTrue(newVm.uiState.value.canSave)

        newVm.updateSelectedPeriods(emptySet())
        assertFalse(newVm.uiState.value.canSave)
        newVm.updateSelectedPeriods(setOf(1))
        assertTrue(newVm.uiState.value.canSave)

        newVm.clearWeeks()
        assertFalse(newVm.uiState.value.canSave)
        newVm.selectAllWeeks()
        assertTrue(newVm.uiState.value.canSave)

        newVm.updateTeacher("师".repeat(51))
        assertEquals(CourseValidationError.TeacherTooLong, newVm.uiState.value.validationError)
        assertFalse(newVm.uiState.value.canSave)
        newVm.updateTeacher("李老师")
        assertNull(newVm.uiState.value.validationError)
        assertTrue(newVm.uiState.value.canSave)

        // 为已有课程添加时段：默认字段齐全，无需再编辑即可直接保存
        val addVm = CourseEditViewModel(repository, timetableId = 1L, courseId = 1L)
        advanceUntilIdle()
        assertTrue(addVm.uiState.value.canSave)

        // 编辑已有时段：未改动时不可保存，改动后可保存，改回原值后恢复不可保存
        val editVm = CourseEditViewModel(repository, timetableId = 1L, courseId = 1L, sessionId = 10L)
        advanceUntilIdle()
        assertFalse(editVm.uiState.value.canSave)

        editVm.updateLocation("实验楼-502")
        assertTrue(editVm.uiState.value.canSave)

        editVm.updateLocation("实验楼-401")
        assertFalse(editVm.uiState.value.canSave)
    }

    private class FakeTimetableRepository : TimetableRepository {
        private var nextCourseId = 10L
        private var nextSessionId = 50L

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
                    course = CourseEntity(id = 1L, timetableId = 1L, name = "数据结构"),
                    sessions = listOf(
                        CourseSessionEntity(
                            id = 10L,
                            courseId = 1L,
                            teacher = "赵老师",
                            location = "实验楼-401",
                            dayOfWeek = 2,
                            periods = listOf(1, 2),
                            weeks = (1..22).toList(),
                        ),
                    ),
                ),
            ),
        )

        override fun observeLessonTimes(timetableId: Long): Flow<List<LessonTimeEntity>> = lessonTimesFlow

        override fun observeCoursesWithSessions(timetableId: Long): Flow<List<CourseWithSessions>> = coursesFlow

        override suspend fun insertCourseSessionWithReminders(
            timetableId: Long,
            session: CourseSessionEntity,
        ): Long {
            val id = ++nextSessionId
            val inserted = session.copy(id = id)
            coursesFlow.update { list ->
                list.map { item ->
                    if (item.course.id == session.courseId) {
                        item.copy(sessions = item.sessions + inserted)
                    } else {
                        item
                    }
                }
            }
            return id
        }

        override suspend fun updateCourseSessionWithReminders(
            timetableId: Long,
            session: CourseSessionEntity,
        ) {
            coursesFlow.update { list ->
                val cleaned = list.map { item ->
                    item.copy(sessions = item.sessions.filterNot { it.id == session.id })
                }
                cleaned.map { item ->
                    if (item.course.id == session.courseId) {
                        item.copy(sessions = item.sessions + session)
                    } else {
                        item
                    }
                }
            }
        }

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
        ): Pair<Long, Long> {
            val trimmed = courseName.trim()
            val existing = coursesFlow.value.firstOrNull { it.course.name == trimmed }
            val resolvedCourseId = existing?.course?.id ?: run {
                val newId = ++nextCourseId
                coursesFlow.update { it + CourseWithSessions(CourseEntity(newId, timetableId, trimmed), emptyList()) }
                newId
            }
            val sessionId = insertCourseSessionWithReminders(
                timetableId = timetableId,
                session = session.copy(courseId = resolvedCourseId),
            )
            return resolvedCourseId to sessionId
        }

        override suspend fun updateCourseWithReminders(course: CourseEntity) = Unit
        override suspend fun deleteCourseWithReminders(course: CourseEntity) = Unit
        override suspend fun restoreCourseWithSessionsWithReminders(
            course: CourseEntity,
            sessions: List<CourseSessionEntity>,
        ): Long = 0L
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
