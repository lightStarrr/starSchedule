package com.star.schedule.ui.layouts

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

// 定义一节课的时间范围
data class LessonTime(
    val period: Int,          // 第几节
    val startTime: String,    // 开始时间，例如 "08:00"
    val endTime: String       // 结束时间，例如 "08:45"
)

// 定义一门课
data class Course(
    val name: String,          // 课程名称
    val location: String,      // 上课地点
    val dayOfWeek: Int,        // 星期几（1=周一, 7=周日）
    val periods: List<Int>     // 对应的节次，例如 [1,2] 表示第1-2节
)

data class CourseBlock(
    val course: Course,
    val dayOfWeek: Int,
    val startPeriod: Int,
    val endPeriod: Int
)


@Composable
fun DateRange(content: Activity){
    val courses = listOf(
        Course("高等数学", "教学楼A101", dayOfWeek = 1, periods = listOf(1, 2)),
        Course("大学英语", "教学楼B202", dayOfWeek = 3, periods = listOf(3, 4)),
        Course("操作系统", "实验楼C303", dayOfWeek = 5, periods = listOf(1, 2, 3)),
    )

    val lessonTimes = listOf(
        LessonTime(1, "08:00", "08:45"),
        LessonTime(2, "08:55", "09:40"),
        LessonTime(3, "10:00", "10:45"),
        LessonTime(4, "10:55", "11:40"),
        LessonTime(5, "14:00", "14:45"),
        LessonTime(6, "14:55", "15:40")
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ){
        ScheduleScreen(courses, lessonTimes)
    }
}

fun buildCourseBlocks(courses: List<Course>): List<CourseBlock> {
    val blocks = mutableListOf<CourseBlock>()
    for (course in courses) {
        val sorted = course.periods.sorted()
        var start = sorted.first()
        var prev = start
        for (i in 1 until sorted.size) {
            if (sorted[i] != prev + 1) {
                // 出现断开，生成一个区块
                blocks.add(CourseBlock(course, course.dayOfWeek, start, prev))
                start = sorted[i]
            }
            prev = sorted[i]
        }
        // 最后一个区块
        blocks.add(CourseBlock(course, course.dayOfWeek, start, prev))
    }
    return blocks
}


@Composable
fun ScheduleScreen(
    courses: List<Course>,
    lessonTimes: List<LessonTime>
) {
    val daysOfWeek = listOf("一", "二", "三", "四", "五", "六", "日")
    val courseBlocks = buildCourseBlocks(courses)
    val cellHeight = 70.dp

    Column(modifier = Modifier.fillMaxSize()) {
        // 星期标题行
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f).padding(4.dp)) // 左上角空白
            daysOfWeek.forEach {
                Text(
                    text = "周$it",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }

        // 每一行表示一节课
        lessonTimes.forEach { lesson ->
            Row(modifier = Modifier.fillMaxWidth()) {
                // 左边显示节次和时间
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(4.dp)
                ) {
                    Text("第${lesson.period}节", style = MaterialTheme.typography.labelMedium)
                    Text("${lesson.startTime}\n${lesson.endTime}", style = MaterialTheme.typography.bodySmall)
                }

                // 右边 7 列
                for (day in 1..7) {
                    // 检查是否有 block 从这一节课开始
                    val block = courseBlocks.find {
                        it.dayOfWeek == day && it.startPeriod == lesson.period
                    }
                    // 如果某个 block 覆盖了这个格子，但不是起点，就跳过
                    val covered = courseBlocks.any {
                        it.dayOfWeek == day &&
                                it.startPeriod < lesson.period &&
                                it.endPeriod >= lesson.period
                    }
                    if (covered) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else if (block != null) {
                        // 合并高度 = 节次数量 * cellHeight
                        val span = block.endPeriod - block.startPeriod + 1
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                                .height(cellHeight * span)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(block.course.name, style = MaterialTheme.typography.bodyMedium)
                                Text(block.course.location, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        // 空白格
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp)
                                .height(cellHeight)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }
            }
        }
    }
}
