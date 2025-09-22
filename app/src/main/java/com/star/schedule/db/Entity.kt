package com.star.schedule.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.TypeConverters

// 偏好设置
@Entity(tableName = "preference")
data class PreferenceEntity(
    @PrimaryKey val prefKey: String,   // 例如 "current_timetable"
    val value: String
)

// 课程表
@Entity(tableName = "timetable")
data class TimetableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,            // 课程表名
    val showWeekend: Boolean,    // 是否显示周六周日
    val startDate: String,       // 学期开始日期
)

// 一节课的时间范围（依赖课程表）
@Entity(
    tableName = "lesson_time",
    foreignKeys = [
        ForeignKey(
            entity = TimetableEntity::class,
            parentColumns = ["id"],
            childColumns = ["timetableId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["timetableId"])]
)
data class LessonTimeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timetableId: Long,        // 属于哪个课程表
    val period: Int,              // 第几节
    val startTime: String,        // 开始时间 "08:00"
    val endTime: String           // 结束时间 "08:45"
)

// 一门课（依赖课程表）
@Entity(
    tableName = "course",
    foreignKeys = [
        ForeignKey(
            entity = TimetableEntity::class,
            parentColumns = ["id"],
            childColumns = ["timetableId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["timetableId"])]
)
@TypeConverters(Converters::class)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timetableId: Long,        // 属于哪个课程表
    val name: String,             // 课程名称
    val location: String,         // 上课地点
    val dayOfWeek: Int,           // 星期几（1=周一, 7=周日）
    val periods: List<Int>,       // 上课节次，例如 [1,2]
    val weeks: List<Int>          // 上课周数，例如 [1,2,3,4,5,6,7]
)
