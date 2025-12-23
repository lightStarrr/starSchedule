package com.star.schedule.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

// 偏好设置
@Entity(tableName = "preference")
data class PreferenceEntity(
    @PrimaryKey val prefKey: String,
    val value: String
)

// 课程表
@Entity(tableName = "timetable")
data class TimetableEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,            // 课程表名
    val showWeekend: Boolean,    // 是否显示周六周日
    val startDate: String,       // 学期开始日期
    val showFuture: Boolean = false, // 是否显示未来的课程
    val rowHeight: Int = 60,     // 课时行高度，默认60dp
    val reminderTime: Int = 15   // 课前提醒时间，默认15分钟
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

// 课程时间模板（全局）
@Entity(
    tableName = "lesson_time_template",
    indices = [Index(value = ["name"], unique = true)]
)
data class LessonTimeTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,              // 模板名称（唯一）
    val createdAt: Long,           // 创建时间（epoch millis）
    val updatedAt: Long            // 更新时间（epoch millis）
)

// 模板中的单节时间
@Entity(
    tableName = "lesson_time_template_item",
    foreignKeys = [
        ForeignKey(
            entity = LessonTimeTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["templateId"]),
        Index(value = ["templateId", "period"], unique = true)
    ]
)
data class LessonTimeTemplateItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val period: Int,
    val startTime: String,
    val endTime: String
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
    val teacher: String,          // 教师名称（可选）
    val location: String,         // 上课地点
    val dayOfWeek: Int,           // 星期几（1=周一, 7=周日）
    val periods: List<Int>,       // 上课节次，例如 [1,2]
    val weeks: List<Int>          // 上课周数，例如 [1,2,3,4,5,6,7]
)

@Entity(tableName = "reminder")
data class ReminderEntity(
    @PrimaryKey val requestCode: Int,
    val courseId: Long,
    val date: String,   // yyyy-MM-dd
    val period: Int
)

// 每天的便签（依赖课程表）
@Entity(
    tableName = "day_note",
    foreignKeys = [
        ForeignKey(
            entity = TimetableEntity::class,
            parentColumns = ["id"],
            childColumns = ["timetableId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["timetableId", "date"], unique = true)]
)
data class DayNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timetableId: Long,    // 属于哪个课程表
    val date: String,         // 对应的具体日期 yyyy-MM-dd
    val content: String       // 便签内容
)
