package com.star.schedule.ui.layouts

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.star.schedule.db.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun TimetableSettings(content: Activity, dao: ScheduleDao) {
    val scope = rememberCoroutineScope()
    val timetables by dao.getAllTimetables().collectAsState(initial = emptyList())
    val expandedMap = remember { mutableStateMapOf<Long, Boolean>() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "课表管理",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        items(timetables) { timetable ->
            val expanded = expandedMap[timetable.id] ?: false

            var showAddLessonDialog by remember { mutableStateOf(false) }
            var showAddCourseDialog by remember { mutableStateOf(false) }
            var showEditLessonDialog by remember { mutableStateOf<LessonTimeEntity?>(null) }
            var showEditCourseDialog by remember { mutableStateOf<CourseEntity?>(null) }

            if (showAddLessonDialog) {
                AddLessonTimeDialog(timetableId = timetable.id, onDismiss = { showAddLessonDialog = false }, dao = dao)
            }

            if (showAddCourseDialog) {
                AddCourseDialog(timetableId = timetable.id, onDismiss = { showAddCourseDialog = false }, dao = dao)
            }

            showEditLessonDialog?.let { lesson ->
                EditLessonTimeDialog(lesson = lesson, onDismiss = { showEditLessonDialog = null }, dao = dao)
            }

            showEditCourseDialog?.let { course ->
                EditCourseDialog(course = course, onDismiss = { showEditCourseDialog = null }, dao = dao)
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedMap[timetable.id] = !expanded },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically, // 行内垂直居中
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(timetable.name, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { scope.launch { dao.deleteTimetable(timetable) } }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "删除课表")
                        }
                    }

                    AnimatedVisibility(visible = expanded) {
                        Column(modifier = Modifier.fillMaxWidth()) {

                            Spacer(Modifier.height(12.dp))

                            // 课表信息
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    var name by remember { mutableStateOf(timetable.name) }
                                    var startDate by remember { mutableStateOf(timetable.startDate) }
                                    var showWeekend by remember { mutableStateOf(timetable.showWeekend) }

                                    OutlinedTextField(
                                        value = name,
                                        onValueChange = { name = it },
                                        label = { Text("课表名称") },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = startDate,
                                        onValueChange = { startDate = it },
                                        label = { Text("开学日期 (YYYY-MM-DD)") },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically, // 行内居中
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("显示周末")
                                        Switch(
                                            checked = showWeekend,
                                            onCheckedChange = { showWeekend = it }
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            scope.launch {
                                                dao.updateTimetable(
                                                    timetable.copy(
                                                        name = name,
                                                        startDate = startDate,
                                                        showWeekend = showWeekend
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("保存修改")
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // 课程时间管理
                            val lessonTimes by dao.getLessonTimesFlow(timetable.id).collectAsState(initial = emptyList())
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("课程时间管理", style = MaterialTheme.typography.titleSmall)
                                    lessonTimes.forEach { lesson ->
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically, // 行内垂直居中
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("第${lesson.period}节 ${lesson.startTime}-${lesson.endTime}")
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(onClick = { showEditLessonDialog = lesson }) {
                                                    Icon(Icons.Rounded.Edit, contentDescription = "编辑课程时间")
                                                }
                                                IconButton(onClick = { scope.launch { dao.deleteLessonTime(lesson) } }) {
                                                    Icon(Icons.Rounded.Delete, contentDescription = "删除课程时间")
                                                }
                                            }
                                        }
                                    }

                                    Button(onClick = { showAddLessonDialog = true }, modifier = Modifier.fillMaxWidth()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.Add, contentDescription = null)
                                            Spacer(Modifier.width(4.dp))
                                            Text("新增课程时间")
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            // 课程管理
                            val courses by dao.getCoursesFlow(timetable.id).collectAsState(initial = emptyList())
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("课程管理", style = MaterialTheme.typography.titleSmall)
                                    courses.forEach { course ->
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically, // 行内垂直居中
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("${course.name} (周${course.dayOfWeek} 节次:${course.periods.joinToString(",")})")
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(onClick = { showEditCourseDialog = course }) {
                                                    Icon(Icons.Rounded.Edit, contentDescription = "编辑课程")
                                                }
                                                IconButton(onClick = { scope.launch { dao.deleteCourse(course) } }) {
                                                    Icon(Icons.Rounded.Delete, contentDescription = "删除课程")
                                                }
                                            }
                                        }
                                    }

                                    Button(onClick = { showAddCourseDialog = true }, modifier = Modifier.fillMaxWidth()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.Add, contentDescription = null)
                                            Spacer(Modifier.width(4.dp))
                                            Text("新增课程")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 新建课表
        item {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch {
                        dao.insertTimetable(
                            TimetableEntity(
                                name = "新建课表",
                                showWeekend = true,
                                startDate = LocalDate.now().toString()
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("新建课表")
                }
            }
        }
    }
}

// ---------- 编辑课程时间弹窗 ----------
@Composable
fun EditLessonTimeDialog(lesson: LessonTimeEntity, onDismiss: () -> Unit, dao: ScheduleDao) {
    var period by remember { mutableStateOf(lesson.period.toString()) }
    var startTime by remember { mutableStateOf(lesson.startTime) }
    var endTime by remember { mutableStateOf(lesson.endTime) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑课程时间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = period,
                    onValueChange = { period = it.filter { c -> c.isDigit() } },
                    label = { Text("节次") }
                )
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = { Text("开始时间 (HH:mm)") }
                )
                OutlinedTextField(
                    value = endTime,
                    onValueChange = { endTime = it },
                    label = { Text("结束时间 (HH:mm)") }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val p = period.toIntOrNull() ?: 1
                scope.launch { dao.updateLessonTime(lesson.copy(period = p, startTime = startTime, endTime = endTime)) }
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ---------- 编辑课程弹窗 ----------
@Composable
fun EditCourseDialog(course: CourseEntity, onDismiss: () -> Unit, dao: ScheduleDao) {
    var name by remember { mutableStateOf(course.name) }
    var location by remember { mutableStateOf(course.location) }
    var dayOfWeek by remember { mutableStateOf(course.dayOfWeek.toString()) }
    var periods by remember { mutableStateOf(course.periods.joinToString(",")) }
    var weeks by remember { mutableStateOf(course.weeks.joinToString(",")) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑课程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("课程名称") })
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("地点") })
                OutlinedTextField(
                    value = dayOfWeek,
                    onValueChange = { dayOfWeek = it.filter { c -> c.isDigit() } },
                    label = { Text("星期 (1-7)") }
                )
                OutlinedTextField(value = periods, onValueChange = { periods = it }, label = { Text("节次 (如 1,2,3)") })
                OutlinedTextField(value = weeks, onValueChange = { weeks = it }, label = { Text("周次 (如 1,2,3)") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val day = dayOfWeek.toIntOrNull() ?: 1
                val periodList = periods.split(",").mapNotNull { it.toIntOrNull() }
                val weekList = weeks.split(",").mapNotNull { it.toIntOrNull() }
                scope.launch {
                    dao.updateCourse(
                        course.copy(
                            name = name.ifBlank { "新课程" },
                            location = location,
                            dayOfWeek = day,
                            periods = periodList.ifEmpty { listOf(1) },
                            weeks = weekList.ifEmpty { listOf(1) }
                        )
                    )
                }
                onDismiss()
            }) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ------------------ 新增课程时间弹窗 ------------------
@Composable
fun AddLessonTimeDialog(
    timetableId: Long,
    onDismiss: () -> Unit,
    dao: ScheduleDao
) {
    var period by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("08:00") }
    var endTime by remember { mutableStateOf("08:45") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增课程时间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = period,
                    onValueChange = { period = it.filter { c -> c.isDigit() } },
                    label = { Text("节次") }
                )
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = { Text("开始时间 (HH:mm)") }
                )
                OutlinedTextField(
                    value = endTime,
                    onValueChange = { endTime = it },
                    label = { Text("结束时间 (HH:mm)") }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val p = period.toIntOrNull() ?: 1
                scope.launch {
                    dao.insertLessonTime(
                        LessonTimeEntity(
                            timetableId = timetableId,
                            period = p,
                            startTime = startTime,
                            endTime = endTime
                        )
                    )
                }
                onDismiss()
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// ------------------ 新增课程弹窗 ------------------
@Composable
fun AddCourseDialog(
    timetableId: Long,
    onDismiss: () -> Unit,
    dao: ScheduleDao
) {
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var dayOfWeek by remember { mutableStateOf("1") }
    var periods by remember { mutableStateOf("1") }
    var weeks by remember { mutableStateOf("1") }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增课程") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("课程名称") })
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("地点") })
                OutlinedTextField(
                    value = dayOfWeek,
                    onValueChange = { dayOfWeek = it.filter { c -> c.isDigit() } },
                    label = { Text("星期 (1-7)") }
                )
                OutlinedTextField(value = periods, onValueChange = { periods = it }, label = { Text("节次 (如 1,2,3)") })
                OutlinedTextField(value = weeks, onValueChange = { weeks = it }, label = { Text("周次 (如 1,2,3)") })
            }
        },
        confirmButton = {
            Button(onClick = {
                val day = dayOfWeek.toIntOrNull() ?: 1
                val periodList = periods.split(",").mapNotNull { it.toIntOrNull() }
                val weekList = weeks.split(",").mapNotNull { it.toIntOrNull() }
                scope.launch {
                    dao.insertCourse(
                        CourseEntity(
                            timetableId = timetableId,
                            name = name.ifBlank { "新课程" },
                            location = location,
                            dayOfWeek = day,
                            periods = periodList.ifEmpty { listOf(1) },
                            weeks = weekList.ifEmpty { listOf(1) }
                        )
                    )
                }
                onDismiss()
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}