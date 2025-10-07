package com.star.schedule.utils

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
        fun validateCourseName(name: String): ValidationResult {
            return when {
                name.isBlank() -> ValidationResult(false, "课程名称不能为空")
                name.length > 50 -> ValidationResult(false, "课程名称不能超过50个字符")
                else -> ValidationResult(true)
            }
        }

        /**
         * 验证上课地点
         */
        fun validateLocation(location: String): ValidationResult {
            return when {
                location.length > 100 -> ValidationResult(false, "上课地点不能超过100个字符")
                else -> ValidationResult(true)
            }
        }

        /**
         * 验证星期几
         */
        fun validateDayOfWeek(dayOfWeek: String): ValidationResult {
            val day = dayOfWeek.toIntOrNull()
            return when {
                day == null -> ValidationResult(false, "星期必须是数字")
                day < 1 || day > 7 -> ValidationResult(false, "星期必须在1-7之间（1=周一，7=周日）")
                else -> ValidationResult(true)
            }
        }

        /**
         * 验证节次
         */
        fun validatePeriods(periods: String): ValidationResult {
            if (periods.isBlank()) {
                return ValidationResult(false, "节次不能为空")
            }
            
            try {
                val periodList = periods.split(",").map { it.trim() }
                if (periodList.isEmpty()) {
                    return ValidationResult(false, "至少需要一个节次")
                }
                
                val periodNumbers = mutableListOf<Int>()
                
                for (period in periodList) {
                    if (period.contains("-")) {
                        // 处理范围格式，如 "1-7"
                        val range = period.split("-")
                        if (range.size != 2) {
                            return ValidationResult(false, "节次范围格式错误，应为：开始-结束")
                        }
                        
                        val start = range[0].toIntOrNull() ?: return ValidationResult(false, "节次范围开始值必须是数字")
                        val end = range[1].toIntOrNull() ?: return ValidationResult(false, "节次范围结束值必须是数字")
                        
                        if (start > end) {
                            return ValidationResult(false, "节次范围开始值不能大于结束值")
                        }
                        
                        if (start < 1 || end > 20) {
                            return ValidationResult(false, "节次范围必须在1-20之间")
                        }
                        
                        periodNumbers.addAll(start..end)
                    } else {
                        // 处理单个数字
                        val periodNum = period.toIntOrNull() ?: return ValidationResult(false, "节次必须是数字，格式如：1,2,3 或 1-7")
                        
                        if (periodNum < 1 || periodNum > 20) {
                            return ValidationResult(false, "节次必须在1-20之间")
                        }
                        
                        periodNumbers.add(periodNum)
                    }
                }
                
                if (periodNumbers.any { it < 1 || it > 20 }) {
                    return ValidationResult(false, "节次必须在1-20之间")
                }
                
                if (periodNumbers.size != periodNumbers.toSet().size) {
                    return ValidationResult(false, "节次不能重复")
                }
                
                return ValidationResult(true)
            } catch (e: Exception) {
                return ValidationResult(false, "节次格式错误，支持：1,2,3 或 1-7")
            }
        }

        /**
         * 验证周次
         */
        fun validateWeeks(weeks: String): ValidationResult {
            if (weeks.isBlank()) {
                return ValidationResult(false, "周次不能为空")
            }
            
            try {
                val weekList = weeks.split(",").map { it.trim() }
                if (weekList.isEmpty()) {
                    return ValidationResult(false, "至少需要一个周次")
                }
                
                val weekNumbers = mutableListOf<Int>()
                
                for (week in weekList) {
                    if (week.contains("-")) {
                        // 处理范围格式，如 "1-7"
                        val range = week.split("-")
                        if (range.size != 2) {
                            return ValidationResult(false, "周次范围格式错误，应为：开始-结束")
                        }
                        
                        val start = range[0].toIntOrNull() ?: return ValidationResult(false, "周次范围开始值必须是数字")
                        val end = range[1].toIntOrNull() ?: return ValidationResult(false, "周次范围结束值必须是数字")
                        
                        if (start > end) {
                            return ValidationResult(false, "周次范围开始值不能大于结束值")
                        }
                        
                        if (start < 1 || end > 30) {
                            return ValidationResult(false, "周次范围必须在1-30之间")
                        }
                        
                        weekNumbers.addAll(start..end)
                    } else {
                        // 处理单个数字
                        val weekNum = week.toIntOrNull() ?: return ValidationResult(false, "周次必须是数字，格式如：1,2,3 或 1-7")
                        
                        if (weekNum < 1 || weekNum > 30) {
                            return ValidationResult(false, "周次必须在1-30之间")
                        }
                        
                        weekNumbers.add(weekNum)
                    }
                }
                
                if (weekNumbers.any { it < 1 || it > 30 }) {
                    return ValidationResult(false, "周次必须在1-30之间")
                }
                
                if (weekNumbers.size != weekNumbers.toSet().size) {
                    return ValidationResult(false, "周次不能重复")
                }
                
                return ValidationResult(true)
            } catch (e: Exception) {
                return ValidationResult(false, "周次格式错误，支持：1,2,3 或 1-7")
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
            weeks: String
        ): ValidationResult {
            val nameResult = validateCourseName(name)
            if (!nameResult.isValid) return nameResult
            
            val locationResult = validateLocation(location)
            if (!locationResult.isValid) return locationResult
            
            val dayResult = validateDayOfWeek(dayOfWeek)
            if (!dayResult.isValid) return dayResult
            
            val periodsResult = validatePeriods(periods)
            if (!periodsResult.isValid) return periodsResult
            
            val weeksResult = validateWeeks(weeks)
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
        fun validatePeriod(period: String): ValidationResult {
            val periodNum = period.toIntOrNull()
            return when {
                periodNum == null -> ValidationResult(false, "节次必须是数字")
                periodNum < 1 || periodNum > 20 -> ValidationResult(false, "节次必须在1-20之间")
                else -> ValidationResult(true)
            }
        }

        /**
         * 验证时间格式
         */
        fun validateTimeFormat(time: String, fieldName: String): ValidationResult {
            if (time.isBlank()) {
                return ValidationResult(false, "${fieldName}不能为空")
            }
            
            try {
                LocalTime.parse(time, timeFormatter)
                return ValidationResult(true)
            } catch (e: DateTimeParseException) {
                return ValidationResult(false, "${fieldName}格式错误，请使用HH:mm格式，如：08:00")
            }
        }

        /**
         * 验证开始时间和结束时间的逻辑关系
         */
        fun validateTimeRange(startTime: String, endTime: String): ValidationResult {
            try {
                val start = LocalTime.parse(startTime, timeFormatter)
                val end = LocalTime.parse(endTime, timeFormatter)
                
                if (start.isAfter(end) || start.equals(end)) {
                    return ValidationResult(false, "结束时间必须晚于开始时间")
                }
                
                return ValidationResult(true)
            } catch (e: DateTimeParseException) {
                return ValidationResult(false, "时间格式错误")
            }
        }

        /**
         * 验证完整的课时数据
         */
        fun validateLessonTimeData(
            period: String,
            startTime: String,
            endTime: String
        ): ValidationResult {
            val periodResult = validatePeriod(period)
            if (!periodResult.isValid) return periodResult
            
            val startTimeResult = validateTimeFormat(startTime, "开始时间")
            if (!startTimeResult.isValid) return startTimeResult
            
            val endTimeResult = validateTimeFormat(endTime, "结束时间")
            if (!endTimeResult.isValid) return endTimeResult
            
            val timeRangeResult = validateTimeRange(startTime, endTime)
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
        fun checkLength(value: String, maxLength: Int, fieldName: String): ValidationResult {
            return when {
                value.length > maxLength -> ValidationResult(false, "${fieldName}不能超过${maxLength}个字符")
                else -> ValidationResult(true)
            }
        }

        /**
         * 检查数字范围
         */
        fun checkIntRange(value: String, min: Int, max: Int, fieldName: String): ValidationResult {
            val num = value.toIntOrNull()
            return when {
                num == null -> ValidationResult(false, "${fieldName}必须是数字")
                num < min || num > max -> ValidationResult(false, "${fieldName}必须在${min}-${max}之间")
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
        fun validateTimetableName(name: String): ValidationResult {
            return when {
                name.isBlank() -> ValidationResult(false, "课程表名称不能为空")
                name.length > 100 -> ValidationResult(false, "课程表名称不能超过100个字符")
                else -> ValidationResult(true)
            }
        }

        /**
         * 验证学期开始日期
         */
        fun validateStartDate(dateStr: String): ValidationResult {
            if (dateStr.isBlank()) {
                return ValidationResult(false, "学期开始日期不能为空")
            }
            
            try {
                val date = LocalDate.parse(dateStr, dateFormatter)
                
                // 检查日期是否在合理范围内（2000年至2050年）
                val currentYear = LocalDate.now().year
                if (date.year < 2000 || date.year > 2050) {
                    return ValidationResult(false, "学期开始日期年份必须在2000-2050年之间")
                }
                
                return ValidationResult(true)
            } catch (e: DateTimeParseException) {
                return ValidationResult(false, "日期格式错误，请使用yyyy-MM-dd格式，如：2024-09-01")
            }
        }

        /**
         * 验证完整的课程表数据
         */
        fun validateTimetableData(
            name: String,
            startDate: String
        ): ValidationResult {
            val nameResult = validateTimetableName(name)
            if (!nameResult.isValid) return nameResult
            
            val dateResult = validateStartDate(startDate)
            if (!dateResult.isValid) return dateResult
            
            return ValidationResult(true)
        }
    }
}