package com.star.schedule.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.star.schedule.core.database.entity.CourseEntity
import com.star.schedule.core.database.entity.CourseSessionEntity
import com.star.schedule.core.database.entity.CourseWithSessions
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CourseDao {
    @Transaction
    @Query("SELECT * FROM course WHERE timetableId = :timetableId ORDER BY id ASC")
    abstract fun getCoursesWithSessionsFlow(timetableId: Long): Flow<List<CourseWithSessions>>

    @Transaction
    @Query("SELECT * FROM course ORDER BY id ASC")
    abstract fun getAllCoursesWithSessionsFlow(): Flow<List<CourseWithSessions>>

    @Query("SELECT * FROM course WHERE timetableId = :timetableId AND name = :name LIMIT 1")
    abstract suspend fun findCourseByNameOnce(timetableId: Long, name: String): CourseEntity?

    @Insert
    abstract suspend fun insertCourse(course: CourseEntity): Long

    @Update
    abstract suspend fun updateCourse(course: CourseEntity)

    @Delete
    abstract suspend fun deleteCourse(course: CourseEntity)

    @Query("DELETE FROM course WHERE timetableId = :timetableId")
    abstract suspend fun deleteCoursesByTimetableId(timetableId: Long)

    @Insert
    abstract suspend fun insertSession(session: CourseSessionEntity): Long

    @Update
    abstract suspend fun updateSession(session: CourseSessionEntity)

    @Delete
    abstract suspend fun deleteSession(session: CourseSessionEntity)

    @Transaction
    open suspend fun insertCourseWithSessions(
        course: CourseEntity,
        sessions: List<CourseSessionEntity>,
    ): Long {
        val courseId = insertCourse(course)
        sessions.forEach { session ->
            insertSession(session.copy(id = 0L, courseId = courseId))
        }
        return courseId
    }

    @Transaction
    open suspend fun upsertCourseAndInsertSession(
        timetableId: Long,
        courseName: String,
        session: CourseSessionEntity,
    ): Pair<Long, Long> {
        val trimmedName = courseName.trim()
        val existingCourse = findCourseByNameOnce(timetableId, trimmedName)
        val courseId = existingCourse?.id ?: insertCourse(
            CourseEntity(
                timetableId = timetableId,
                name = trimmedName,
            ),
        )
        val sessionId = insertSession(session.copy(id = 0L, courseId = courseId))
        return courseId to sessionId
    }

    @Transaction
    open suspend fun replaceCoursesForTimetable(
        timetableId: Long,
        items: List<Pair<CourseEntity, List<CourseSessionEntity>>>,
    ) {
        deleteCoursesByTimetableId(timetableId)
        items.forEach { (course, sessions) ->
            val courseId = insertCourse(course.copy(id = 0L, timetableId = timetableId))
            sessions.forEach { session ->
                insertSession(session.copy(id = 0L, courseId = courseId))
            }
        }
    }
}
