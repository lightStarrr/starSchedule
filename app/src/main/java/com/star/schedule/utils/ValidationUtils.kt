package com.star.schedule.utils

import android.content.res.Resources
import androidx.annotation.StringRes
import com.star.schedule.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * 数据验证工具类
 * 提供课程和课时数据的验证功能
 */
object ValidationUtils {

    /**
     * 验证结果数据类
     */
    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String = ""
    )

    private fun Resources.text(@StringRes id: Int, vararg args: Any): String = getString(id, *args)

    /**
     * 课程验证
     */
    object CourseValidation {
        
        /**
         * 优化数字列表显示，将连续数字转换为范围格式
         * 例如：[1,2,3,5,7,8,9] -> "1-3,5,7-9"
         */
        fun formatNumberList(numbers: List<Int>): String {
            if (numbers.isEmpty()) return ""
            
            val sorted = numbers.distinct().sorted()
            val result = StringBuilder()
            var start = sorted[0]
            var end = sorted[0]
            
            for (i in 1 until sorted.size) {
                if (sorted[i] == end + 1) {
                    end = sorted[i]
                } else {
                    if (result.isNotEmpty()) result.append(",")
                    if (start == end) {
                        result.append(start)
                    } else {
                        result.append("$start-$end")
                    }
                    start = sorted[i]
                    end = sorted[i]
                }
            }
            
            if (result.isNotEmpty()) result.append(",")
            if (start == end) {
                result.append(start)
            } else {
                result.append("$start-$end")
            }
            
            return result.toString()
        }
        
        /**
         * 验证课程名称
         */
        fun validateCourseName(name: String, resources: Resources): ValidationResult {
            return when {
                name.isBlank() -> ValidationResult(false, resources.text(R.string.error_course_name_required))
                name.length > 50 -> ValidationResult(false, resources.text(R.string.error_course_name_too_long))
                else -> ValidationResult(true)
            }
        }

        /**
         * 验证上课地点
         */
        fun validateLocation(location: String, resources: Resources): ValidationResult {
            return when {
                location.length > 100 -> ValidationResult(false, resources.text(R.string.error_location_too_long))
                else -> ValidationResult(true)
            }
        }

        /**
         * 验证星期几
         */
        fun validateDayOfWeek(dayOfWeek: String, resources: Resources): ValidationResult {
            val day = dayOfWeek.toIntOrNull()
            return when {
                day == null -> ValidationResult(false, resources.text(R.string.error_day_of_week_not_number))
                day < 1 || day > 7 -> ValidationResult(false, resources.text(R.string.error_day_of_week_range))
                else -> ValidationResult(true)
            }
        }

        /**
         * 验证节次
         */
        fun validatePeriods(periods: String, resources: Resources): ValidationResult {
            if (periods.isBlank()) {
                return ValidationResult(false, resources.text(R.string.error_periods_empty))
            }

            try {
                val periodList = periods.split(",").map { it.trim() }
                if (periodList.isEmpty()) {
                    return ValidationResult(false, resources.text(R.string.error_periods_at_least_one))
                }
                
                val periodNumbers = mutableListOf<Int>()
                
                for (period in periodList) {
                    if (period.contains("-")) {
                        // 处理范围格式，如 "1-7"
                        val range = period.split("-")
                        if (range.size != 2) {
                            return ValidationResult(false, resources.text(R.string.error_period_range_format))
                        }

                        val start = range[0].toIntOrNull()
                            ?: return ValidationResult(false, resources.text(R.string.error_period_range_start_number))
                        val end = range[1].toIntOrNull()
                            ?: return ValidationResult(false, resources.text(R.string.error_period_range_end_number))

                        if (start > end) {
                            return ValidationResult(false, resources.text(R.string.error_period_range_start_after_end))
                        }

                        if (start < 1 || end > 20) {
                            return ValidationResult(false, resources.text(R.string.error_period_range))
                        }

                        periodNumbers.addAll(start..end)
                    } else {
                        // 处理单个数字
                        val periodNum = period.toIntOrNull()
                            ?: return ValidationResult(false, resources.text(R.string.error_periods_number_format))

                        if (periodNum < 1 || periodNum > 20) {
                            return ValidationResult(false, resources.text(R.string.error_periods_range))
                        }

                        periodNumbers.add(periodNum)
                    }
                }

                if (periodNumbers.any { it < 1 || it > 20 }) {
                    return ValidationResult(false, resources.text(R.string.error_periods_range))
                }

                if (periodNumbers.size != periodNumbers.toSet().size) {
                    return ValidationResult(false, resources.text(R.string.error_periods_duplicate))
                }

                return ValidationResult(true)
            } catch (e: Exception) {
                return ValidationResult(false, resources.text(R.string.error_periods_format))
            }
        }

        /**
         * 验证周次
         */
        fun validateWeeks(weeks: String, resources: Resources): ValidationResult {
            if (weeks.isBlank()) {
                return ValidationResult(false, resources.text(R.string.error_weeks_empty))
            }

            try {
                val weekList = weeks.split(",").map { it.trim() }
                if (weekList.isEmpty()) {
                    return ValidationResult(false, resources.text(R.string.error_weeks_at_least_one))
                }
                
                val weekNumbers = mutableListOf<Int>()
                
                for (week in weekList) {
                    if (week.contains("-")) {
                        // 处理范围格式，如 "1-7"
                        val range = week.split("-")
                        if (range.size != 2) {
                            return ValidationResult(false, resources.text(R.string.error_week_range_format))
                        }

                        val start = range[0].toIntOrNull()
                            ?: return ValidationResult(false, resources.text(R.string.error_week_range_start_number))
                        val end = range[1].toIntOrNull()
                            ?: return ValidationResult(false, resources.text(R.string.error_week_range_end_number))

                        if (start > end) {
                            return ValidationResult(false, resources.text(R.string.error_week_range_start_after_end))
                        }

                        if (start < 1 || end > 30) {
                            return ValidationResult(false, resources.text(R.string.error_week_range))
                        }

                        weekNumbers.addAll(start..end)
                    } else {
                        // 处理单个数字
                        val weekNum = week.toIntOrNull()
                            ?: return ValidationResult(false, resources.text(R.string.error_weeks_number_format))

                        if (weekNum < 1 || weekNum > 30) {
                            return ValidationResult(false, resources.text(R.string.error_weeks_range))
                        }

                        weekNumbers.add(weekNum)
                    }
                }

                if (weekNumbers.any { it < 1 || it > 30 }) {
                    return ValidationResult(false, resources.text(R.string.error_weeks_range))
                }

                if (weekNumbers.size != weekNumbers.toSet().size) {
                    return ValidationResult(false, resources.text(R.string.error_weeks_duplicate))
                }

                return ValidationResult(true)
            } catch (e: Exception) {
                return ValidationResult(false, resources.text(R.string.error_weeks_format))
            }
        }

        /**
         * 解析范围格式的数字字符串（如 "1,2,3" 或 "1-7"）为数字列表
         */
        fun parseNumberRange(input: String): List<Int> {
            val result = mutableListOf<Int>()
            val parts = input.split(",").map { it.trim() }
            
            for (part in parts) {
                if (part.contains("-")) {
                    // 处理范围格式，如 "1-7"
                    val range = part.split("-")
                    if (range.size == 2) {
                        val start = range[0].toIntOrNull()
                        val end = range[1].toIntOrNull()
                        if (start != null && end != null && start <= end) {
                            result.addAll(start..end)
                        }
                    }
                } else {
                    // 处理单个数字
                    val number = part.toIntOrNull()
                    if (number != null) {
                        result.add(number)
                    }
                }
            }
            
            return result.distinct().sorted()
        }

        /**
         * 验证完整的课程数据
         */
        fun validateCourseData(
            name: String,
            location: String,
            dayOfWeek: String,
            periods: String,
            weeks: String,
            resources: Resources
        ): ValidationResult {
            val nameResult = validateCourseName(name, resources)
            if (!nameResult.isValid) return nameResult

            val locationResult = validateLocation(location, resources)
            if (!locationResult.isValid) return locationResult

            val dayResult = validateDayOfWeek(dayOfWeek, resources)
            if (!dayResult.isValid) return dayResult

            val periodsResult = validatePeriods(periods, resources)
            if (!periodsResult.isValid) return periodsResult

            val weeksResult = validateWeeks(weeks, resources)
            if (!weeksResult.isValid) return weeksResult

            return ValidationResult(true)
        }
    }

    /**
     * 课时验证
     */
    object LessonTimeValidation {
        
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /**
         * 验证节次
         */
        fun validatePeriod(period: String, resources: Resources): ValidationResult {
            val periodNum = period.toIntOrNull()
            return when {
                periodNum == null -> ValidationResult(false, resources.text(R.string.error_period_must_be_number))
                periodNum < 1 || periodNum > 20 -> ValidationResult(false, resources.text(R.string.error_period_must_be_in_range))
                else -> ValidationResult(true)
            }
        }

        /**
         * 验证时间格式
         */
        fun validateTimeFormat(time: String, fieldName: String, resources: Resources): ValidationResult {
            if (time.isBlank()) {
                return ValidationResult(false, resources.text(R.string.error_field_required, fieldName))
            }

            try {
                LocalTime.parse(time, timeFormatter)
                return ValidationResult(true)
            } catch (e: DateTimeParseException) {
                return ValidationResult(false, resources.text(R.string.error_field_time_format, fieldName))
            }
        }

        /**
         * 验证开始时间和结束时间的逻辑关系
         */
        fun validateTimeRange(startTime: String, endTime: String, resources: Resources): ValidationResult {
            try {
                val start = LocalTime.parse(startTime, timeFormatter)
                val end = LocalTime.parse(endTime, timeFormatter)

                if (start.isAfter(end) || start.equals(end)) {
                    return ValidationResult(false, resources.text(R.string.error_end_time_after_start))
                }

                return ValidationResult(true)
            } catch (e: DateTimeParseException) {
                return ValidationResult(false, resources.text(R.string.error_time_format_generic))
            }
        }

        /**
         * 验证完整的课时数据
         */
        fun validateLessonTimeData(
            period: String,
            startTime: String,
            endTime: String,
            resources: Resources
        ): ValidationResult {
            val periodResult = validatePeriod(period, resources)
            if (!periodResult.isValid) return periodResult

            val startTimeResult = validateTimeFormat(startTime, resources.text(R.string.label_start_time), resources)
            if (!startTimeResult.isValid) return startTimeResult

            val endTimeResult = validateTimeFormat(endTime, resources.text(R.string.label_end_time), resources)
            if (!endTimeResult.isValid) return endTimeResult

            val timeRangeResult = validateTimeRange(startTime, endTime, resources)
            if (!timeRangeResult.isValid) return timeRangeResult

            return ValidationResult(true)
        }
    }

    /**
     * 通用验证辅助方法
     */
    object CommonValidation {
        
        /**
         * 检查字符串是否为空或只包含空白字符
         */
        fun isBlankOrEmpty(value: String): Boolean {
            return value.isBlank()
        }

        /**
         * 检查字符串长度
         */
        fun checkLength(value: String, maxLength: Int, fieldName: String, resources: Resources): ValidationResult {
            return when {
                value.length > maxLength -> ValidationResult(false, resources.text(R.string.error_field_length_exceed, fieldName, maxLength))
                else -> ValidationResult(true)
            }
        }

        /**
         * 检查数字范围
         */
        fun checkIntRange(value: String, min: Int, max: Int, fieldName: String, resources: Resources): ValidationResult {
            val num = value.toIntOrNull()
            return when {
                num == null -> ValidationResult(false, resources.text(R.string.error_field_not_number, fieldName))
                num < min || num > max -> ValidationResult(false, resources.text(R.string.error_field_int_range, fieldName, min, max))
                else -> ValidationResult(true)
            }
        }
    }

    /**
     * 课程表验证
     */
    object TimetableValidation {
        
        private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        
        /**
         * 验证课程表名称
         */
        fun validateTimetableName(name: String, resources: Resources): ValidationResult {
            return when {
                name.isBlank() -> ValidationResult(false, resources.text(R.string.error_timetable_name_required))
                name.length > 100 -> ValidationResult(false, resources.text(R.string.error_timetable_name_too_long))
                else -> ValidationResult(true)
            }
        }

        /**
         * 验证学期开始日期
         */
        fun validateStartDate(dateStr: String, resources: Resources): ValidationResult {
            if (dateStr.isBlank()) {
                return ValidationResult(false, resources.text(R.string.error_semester_start_date_required))
            }

            try {
                val date = LocalDate.parse(dateStr, dateFormatter)

                // 检查日期是否在合理范围内（2000年至2050年）
                val currentYear = LocalDate.now().year
                if (date.year < 2000 || date.year > 2050) {
                    return ValidationResult(false, resources.text(R.string.error_semester_start_year_range))
                }

                return ValidationResult(true)
            } catch (e: DateTimeParseException) {
                return ValidationResult(false, resources.text(R.string.error_date_format_detail))
            }
        }

        /**
         * 验证完整的课程表数据
         */
        fun validateTimetableData(
            name: String,
            startDate: String,
            resources: Resources
        ): ValidationResult {
            val nameResult = validateTimetableName(name, resources)
            if (!nameResult.isValid) return nameResult

            val dateResult = validateStartDate(startDate, resources)
            if (!dateResult.isValid) return dateResult

            return ValidationResult(true)
        }
    }
}
