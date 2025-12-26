package com.star.schedule.utils

import android.util.Log
import com.star.schedule.autoupdate.QiangzhiJwAutoUpdateConfig
import com.star.schedule.autoupdate.TimetableAutoUpdateJson
import com.star.schedule.db.CourseEntity
import com.star.schedule.db.LessonTimeEntity
import com.star.schedule.db.ScheduleDao
import com.star.schedule.db.TimetableEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.firstOrNull
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Base64

object QiangzhiJwImporter {
    private const val TAG = "QiangzhiJwImporter"

    // 仅作为示例
    const val EXAMPLE_BASE_URL = "http://jw.lidapoly.edu.cn/shldzyjsxy_jsxsd"

    private data class Endpoints(
        val baseUrl: String,
        val loginUrl: String,
        val mainUrl: String,
        val timetableUrl: String
    )

    private fun normalizeBaseUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null
        return trimmed.removeSuffix("/")
    }

    private fun endpoints(baseUrl: String): Endpoints? {
        val normalized = normalizeBaseUrl(baseUrl) ?: return null
        return Endpoints(
            baseUrl = normalized,
            loginUrl = "$normalized/xk/LoginToXk",
            mainUrl = "$normalized/framework/xsMain.jsp",
            timetableUrl = "$normalized/xskb/xskb_list.do"
        )
    }

    private fun hostLabel(baseUrl: String): String? =
        runCatching { java.net.URI(baseUrl).host }.getOrNull()?.takeIf { it.isNotBlank() }

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"

    sealed interface ImportResult {
        data class Success(val warning: String? = null) : ImportResult
        data class Error(val message: String) : ImportResult
    }

    suspend fun importFromQiangzhiJw(
        baseUrl: String,
        account: String,
        password: String,
        dao: ScheduleDao
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val endpoints = endpoints(baseUrl)
                ?: return@withContext ImportResult.Error("网址格式不正确，请输入形如 http(s)://.../xxx_jsxsd")

            val cookieJar = InMemoryCookieJar()
            val client = OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .followRedirects(false)
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val encodedValue = "${base64(account)}%%%${base64(password)}"
            val requestBody = FormBody.Builder()
                .add("encoded", encodedValue)
                .build()

            val loginRequest = Request.Builder()
                .url(endpoints.loginUrl)
                .post(requestBody)
                .addHeader("User-Agent", USER_AGENT)
                .build()

            val redirectUrl = client.newCall(loginRequest).execute().use { response ->
                val code = response.code
                // 该系统登录成功通常返回 302 重定向到 xsMain.jsp，这里不能用 isSuccessful(只包含2xx) 判断
                if (code !in 300..399) {
                    Log.e(TAG, "Login request unexpected status. http=${response.code}, message=${response.message}")
                    return@withContext ImportResult.Error(
                        if (code in 200..299) "登录失败，请检查账号密码是否正确" else "登录请求失败：HTTP ${response.code}"
                    )
                }

                val location = response.header("Location")
                val resolved = location?.let { loginRequest.url.resolve(it)?.toString() } ?: ""
                if (resolved.isBlank() || !resolved.startsWith(endpoints.mainUrl)) {
                    Log.w(TAG, "Login redirect unexpected. http=${response.code}, location=$location")
                    return@withContext ImportResult.Error("登录失败，请检查账号密码是否正确")
                }
                resolved
            }

            // 尽量模拟浏览器流程：先访问一次主页，便于服务端补齐会话信息/下发 Cookie。
            runCatching {
                client.newCall(
                    Request.Builder()
                        .url(redirectUrl)
                        .get()
                        .addHeader("User-Agent", USER_AGENT)
                        .build()
                ).execute().close()
            }.onFailure {
                Log.w(TAG, "Visit main page failed: ${it.message}")
            }

            val html = client.newCall(
                Request.Builder()
                    .url(endpoints.timetableUrl)
                    .get()
                    .addHeader("User-Agent", USER_AGENT)
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Fetch timetable failed. http=${response.code}, message=${response.message}")
                    return@withContext ImportResult.Error("获取课表失败：HTTP ${response.code}")
                }
                response.body?.string() ?: run {
                    Log.e(TAG, "Response body is null")
                    return@withContext ImportResult.Error("获取课表失败：响应内容为空")
                }
            }

            val parsed = parseTimetableHtml(html)
            if (parsed.courses.isEmpty()) {
                return@withContext ImportResult.Error("未解析到课程，请确认课表页可正常访问")
            }

            val (startDate, warning) = inferStartDate(parsed.termId)
            val timetableName = buildString {
                append("强智教务")
                hostLabel(endpoints.baseUrl)?.let { append(" ").append(it) }
                if (!parsed.termId.isNullOrBlank()) {
                    append(" ").append(parsed.termId)
                }
            }

            val timetableId = dao.insertTimetableWithReminders(
                TimetableEntity(
                    name = timetableName,
                    showWeekend = true,
                    startDate = startDate.toString(),
                    autoUpdateJson = TimetableAutoUpdateJson.encode(
                        QiangzhiJwAutoUpdateConfig(
                            baseUrl = endpoints.baseUrl,
                            account = account,
                            password = password
                        )
                    )
                )
            )

            val maxPeriod = parsed.courses.flatMap { it.periods }.maxOrNull() ?: 0
            defaultLessonTimes(maxPeriod).forEach { slot ->
                dao.insertOrUpdateLessonTimeAutoSort(
                    LessonTimeEntity(
                        timetableId = timetableId,
                        period = slot.period,
                        startTime = slot.startTime,
                        endTime = slot.endTime
                    )
                )
            }

            parsed.courses.forEach { course ->
                dao.insertCourseWithReminders(
                    CourseEntity(
                        timetableId = timetableId,
                        name = course.name,
                        teacher = course.teacher,
                        location = course.location,
                        dayOfWeek = course.dayOfWeek,
                        periods = course.periods,
                        weeks = course.weeks
                    )
                )
            }

            ImportResult.Success(warning = warning)
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            ImportResult.Error("导入失败：${e.message ?: "未知错误"}")
        }
    }

    suspend fun updateCoursesForTimetable(
        timetableId: Long,
        baseUrl: String,
        account: String,
        password: String,
        dao: ScheduleDao
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            val endpoints = endpoints(baseUrl)
                ?: return@withContext ImportResult.Error("网址格式不正确，请检查课表的自动更新配置")

            val timetable = dao.getTimetableFlow(timetableId).firstOrNull()
            if (timetable == null) {
                return@withContext ImportResult.Error("课表不存在，无法更新")
            }

            val cookieJar = InMemoryCookieJar()
            val client = OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .followRedirects(false)
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val encodedValue = "${base64(account)}%%%${base64(password)}"
            val requestBody = FormBody.Builder()
                .add("encoded", encodedValue)
                .build()

            val loginRequest = Request.Builder()
                .url(endpoints.loginUrl)
                .post(requestBody)
                .addHeader("User-Agent", USER_AGENT)
                .build()

            val redirectUrl = client.newCall(loginRequest).execute().use { response ->
                val code = response.code
                // 该系统登录成功通常返回 302 重定向到 xsMain.jsp，这里不能用 isSuccessful(只包含2xx) 判断
                if (code !in 300..399) {
                    Log.e(TAG, "Login request unexpected status. http=${response.code}, message=${response.message}")
                    return@withContext ImportResult.Error(
                        if (code in 200..299) "登录失败，请检查账号密码是否正确" else "登录请求失败：HTTP ${response.code}"
                    )
                }

                val location = response.header("Location")
                val resolved = location?.let { loginRequest.url.resolve(it)?.toString() } ?: ""
                if (resolved.isBlank() || !resolved.startsWith(endpoints.mainUrl)) {
                    Log.w(TAG, "Login redirect unexpected. http=${response.code}, location=$location")
                    return@withContext ImportResult.Error("登录失败，请检查账号密码是否正确")
                }
                resolved
            }

            // 尽量模拟浏览器流程：先访问一次主页，便于服务端补齐会话信息/下发 Cookie。
            runCatching {
                client.newCall(
                    Request.Builder()
                        .url(redirectUrl)
                        .get()
                        .addHeader("User-Agent", USER_AGENT)
                        .build()
                ).execute().close()
            }.onFailure {
                Log.w(TAG, "Visit main page failed: ${it.message}")
            }

            val html = client.newCall(
                Request.Builder()
                    .url(endpoints.timetableUrl)
                    .get()
                    .addHeader("User-Agent", USER_AGENT)
                    .build()
            ).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Fetch timetable failed. http=${response.code}, message=${response.message}")
                    return@withContext ImportResult.Error("获取课表失败：HTTP ${response.code}")
                }
                response.body?.string() ?: run {
                    Log.e(TAG, "Response body is null")
                    return@withContext ImportResult.Error("获取课表失败：响应内容为空")
                }
            }

            val parsed = parseTimetableHtml(html)
            if (parsed.courses.isEmpty()) {
                return@withContext ImportResult.Error("未解析到课程，请确认课表页可正常访问")
            }

            val courseEntities = parsed.courses.map { course ->
                CourseEntity(
                    timetableId = timetableId,
                    name = course.name,
                    teacher = course.teacher,
                    location = course.location,
                    dayOfWeek = course.dayOfWeek,
                    periods = course.periods,
                    weeks = course.weeks
                )
            }

            dao.replaceCoursesForTimetable(timetableId, courseEntities)
            ImportResult.Success()
        } catch (e: Exception) {
            Log.e(TAG, "Update failed", e)
            ImportResult.Error("更新失败：${e.message ?: "未知错误"}")
        }
    }

    private fun base64(input: String): String =
        Base64.getEncoder().encodeToString(input.toByteArray(Charsets.UTF_8))

    private data class ParsedTimetable(
        val termId: String?,
        val courses: List<ParsedCourse>
    )

    private data class ParsedCourse(
        val name: String,
        val teacher: String,
        val location: String,
        val dayOfWeek: Int,
        val periods: List<Int>,
        val weeks: List<Int>
    )

    private val selectedTermRegex = Regex(
        "<option\\s+[^>]*value=[\"']([^\"']+)[\"'][^>]*selected",
        RegexOption.IGNORE_CASE
    )

    private val kbContentDivRegex = Regex(
        "<div\\s+[^>]*id=[\"']([^\"']+)[\"'][^>]*class=[\"']kbcontent[\"'][^>]*>(.*?)</div>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private fun parseTimetableHtml(html: String): ParsedTimetable {
        val termId = selectedTermRegex.find(html)?.groupValues?.get(1)

        val uniqueCourses = LinkedHashMap<String, ParsedCourse>()
        for (match in kbContentDivRegex.findAll(html)) {
            val divId = match.groupValues[1]
            val dayOfWeek = extractDayOfWeek(divId) ?: continue
            val lines = htmlToLines(match.groupValues[2])

            for (block in splitBlocks(lines)) {
                val parsedCourse = parseCourseBlock(block, dayOfWeek) ?: continue
                val key =
                    "${parsedCourse.name}|${parsedCourse.teacher}|${parsedCourse.location}|${parsedCourse.dayOfWeek}|${
                        parsedCourse.weeks.joinToString(",")
                    }|${parsedCourse.periods.joinToString(",")}"
                uniqueCourses[key] = parsedCourse
            }
        }

        return ParsedTimetable(
            termId = termId,
            courses = uniqueCourses.values.toList()
        )
    }

    private fun extractDayOfWeek(divId: String): Int? {
        val parts = divId.split("-")
        val day = parts.getOrNull(parts.size - 2)?.toIntOrNull() ?: return null
        return day.takeIf { it in 1..7 }
    }

    private fun htmlToLines(innerHtml: String): List<String> {
        val text = innerHtml
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .replace("&nbsp;", " ")
            .replace("\u00A0", " ")
            .replace(Regex("<[^>]*>"), "")
        return text.lines().map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun splitBlocks(lines: List<String>): List<List<String>> {
        val blocks = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        for (line in lines) {
            val trimmed = line.trim()
            val isSeparator = trimmed.length >= 5 && trimmed.all { it == '-' }
            if (isSeparator) {
                if (current.isNotEmpty()) {
                    blocks.add(current)
                    current = mutableListOf()
                }
            } else {
                current.add(trimmed)
            }
        }
        if (current.isNotEmpty()) blocks.add(current)
        return blocks
    }

    private val periodRegex = Regex("\\[(\\d{2}(?:-\\d{2})*)节]")

    private fun parseCourseBlock(lines: List<String>, dayOfWeek: Int): ParsedCourse? {
        val cleanLines = lines.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanLines.isEmpty()) return null

        val name = cleanLines[0].substringBefore("[").trim()
        if (name.isBlank()) return null

        val teacher = cleanLines.getOrNull(1)?.trim().orEmpty()
        val location = cleanLines.lastOrNull()?.trim().orEmpty()

        val weekLine = cleanLines.firstOrNull { periodRegex.containsMatchIn(it) } ?: return null
        val periods = periodRegex.find(weekLine)?.groupValues?.get(1)
            ?.split("-")
            ?.mapNotNull { it.toIntOrNull() }
            ?.distinct()
            ?.sorted()
            ?: return null

        val parityHint = weekLine.substringAfter("(", "").substringBefore(")", "")
        val weeksRaw = parseWeeks(weekLine.substringBefore("(").trim())
        val weeks = when {
            parityHint.contains("单") -> weeksRaw.filter { it % 2 == 1 }
            parityHint.contains("双") -> weeksRaw.filter { it % 2 == 0 }
            else -> weeksRaw
        }

        if (weeks.isEmpty() || periods.isEmpty()) return null

        return ParsedCourse(
            name = name,
            teacher = teacher,
            location = location,
            dayOfWeek = dayOfWeek,
            periods = periods,
            weeks = weeks
        )
    }

    private fun parseWeeks(weekPart: String): List<Int> {
        if (weekPart.isBlank()) return emptyList()
        val weeks = mutableSetOf<Int>()
        val parts = weekPart.split(",").map { it.trim() }.filter { it.isNotBlank() }
        for (part in parts) {
            val rangeParts = part.split("-").map { it.trim() }.filter { it.isNotBlank() }
            if (rangeParts.size == 2) {
                val start = rangeParts[0].toIntOrNull()
                val end = rangeParts[1].toIntOrNull()
                if (start != null && end != null && start <= end) {
                    for (w in start..end) weeks.add(w)
                }
            } else {
                val single = part.toIntOrNull()
                if (single != null) weeks.add(single)
            }
        }
        return weeks.sorted()
    }

    private data class LessonSlot(val period: Int, val startTime: String, val endTime: String)

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    private fun defaultLessonTimes(maxPeriod: Int): List<LessonSlot> {
        if (maxPeriod <= 0) return emptyList()

        val base = listOf(
            LessonSlot(1, "08:00", "08:45"),
            LessonSlot(2, "08:55", "09:40"),
            LessonSlot(3, "10:00", "10:45"),
            LessonSlot(4, "10:55", "11:40"),
            LessonSlot(5, "13:00", "13:45"),
            LessonSlot(6, "13:55", "14:40"),
            LessonSlot(7, "15:00", "15:45"),
            LessonSlot(8, "15:55", "16:40"),
            LessonSlot(9, "18:30", "19:15"),
            LessonSlot(10, "19:25", "20:10"),
            LessonSlot(11, "20:20", "21:05"),
            LessonSlot(12, "21:15", "22:00"),
        )

        if (maxPeriod <= base.size) return base.take(maxPeriod)

        val extended = base.toMutableList()
        var cursor = LocalTime.parse(extended.last().endTime, timeFormat)
        for (period in (base.size + 1)..maxPeriod) {
            cursor = cursor.plusMinutes(10)
            val end = cursor.plusMinutes(45)
            extended.add(LessonSlot(period, cursor.format(timeFormat), end.format(timeFormat)))
            cursor = end
        }
        return extended
    }

    private fun inferStartDate(termId: String?): Pair<LocalDate, String?> {
        return LocalDate.now() to "无法确定开学日期，已使用当前日期代替"
    }

    private class InMemoryCookieJar : CookieJar {
        private val store = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            cookies.forEach { cookie ->
                store.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
                store.add(cookie)
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = store.filter { it.matches(url) }
    }
}
