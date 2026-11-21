package com.star.schedule.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.star.schedule.core.database.entity.CourseEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CourseDao {
    @Query("SELECT * FROM course WHERE timetableId = :timetableId ORDER BY dayOfWeek ASC")
    abstract fun getCoursesFlow(timetableId: Long): Flow<List<CourseEntity>>

    @Query("SELECT * FROM course")
    abstract fun getAllCoursesFlow(): Flow<List<CourseEntity>>

    @Insert
    abstract suspend fun insertCourse(course: CourseEntity): Long

    @Update
    abstract suspend fun updateCourse(course: CourseEntity)

    @Delete
    abstract suspend fun deleteCourse(course: CourseEntity)

    @Query("DELETE FROM course WHERE timetableId = :timetableId")
    abstract suspend fun deleteCoursesByTimetableId(timetableId: Long)

    @Transaction
    open suspend fun replaceCoursesForTimetable(timetableId: Long, courses: List<CourseEntity>) {
        deleteCoursesByTimetableId(timetableId)
        courses.forEach { insertCourse(it) }
    }
}
