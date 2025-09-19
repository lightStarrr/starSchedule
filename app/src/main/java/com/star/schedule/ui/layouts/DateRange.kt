package com.star.schedule.ui.layouts

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
    lessonTimes: List<LessonTime>,
    cellHeight: Dp = 70.dp,
    cellPadding: Dp = 2.dp
) {
    val daysOfWeek = listOf("一", "二", "三", "四", "五", "六", "日")
    val courseBlocks = buildCourseBlocks(courses)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalWidth = maxWidth
        val leftColumnWidth = totalWidth / 8  // 左侧节次列宽
        val dayColumnWidth = (totalWidth - leftColumnWidth) / 7  // 每天宽度
        val headerHeight = cellHeight // 顶部标题行高度

        // 背景网格和标题
        Column(modifier = Modifier.fillMaxSize()) {
            // 星期标题行
            Row(modifier = Modifier.height(headerHeight).fillMaxWidth()) {
                Box(modifier = Modifier.width(leftColumnWidth)) // 左上角空白
                daysOfWeek.forEach { day ->
                    Box(
                        modifier = Modifier
                            .width(dayColumnWidth)
                            .padding(cellPadding)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("周$day", textAlign = TextAlign.Center)
                    }
                }
            }

            // 每节课行
            lessonTimes.forEach { lesson ->
                Row(modifier = Modifier.height(cellHeight).fillMaxWidth()) {
                    // 左侧节次列
                    Column(
                        modifier = Modifier.width(leftColumnWidth),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("第${lesson.period}节", textAlign = TextAlign.Center)
                        Text("${lesson.startTime}\n${lesson.endTime}", textAlign = TextAlign.Center)
                    }

                    // 7列课程网格占位
                    for (day in 1..7) {
                        val isOccupied = courseBlocks.any { block ->
                            block.dayOfWeek == day &&
                                    block.startPeriod <= lesson.period &&
                                    block.endPeriod >= lesson.period
                        }

                        Box(
                            modifier = Modifier
                                .width(dayColumnWidth)
                                .height(cellHeight)
                                .padding(cellPadding)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (isOccupied) Color.Transparent
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }
            }
        }

        // 课程块 Overlay，覆盖在网格上
        courseBlocks.forEach { block ->
            val span = block.endPeriod - block.startPeriod + 1
            Box(
                modifier = Modifier
                    .absoluteOffset(
                        x = leftColumnWidth + dayColumnWidth * (block.dayOfWeek - 1) + cellPadding,
                        y = headerHeight + cellHeight * (block.startPeriod - 1) + cellPadding
                    )
                    .width(dayColumnWidth - cellPadding * 2)
                    .height(cellHeight * span - cellPadding * 2)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(block.course.name, style = MaterialTheme.typography.bodyMedium)
                    Text(block.course.location, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

