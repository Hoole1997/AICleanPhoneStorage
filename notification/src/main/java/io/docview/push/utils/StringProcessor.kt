package io.docview.push.utils

/**
 * 字符串处理工具
 */
object StringProcessor {

    /**
     * 字符串反转
     */
    fun reverse(input: String): String {
        return input.reversed()
    }

    /**
     * 字符串转大写
     */
    fun toUpperCase(input: String): String {
        return input.uppercase()
    }

    /**
     * 字符串转小写
     */
    fun toLowerCase(input: String): String {
        return input.lowercase()
    }

    /**
     * 移除空格
     */
    fun removeSpaces(input: String): String {
        return input.replace(" ", "")
    }

    /**
     * 截断字符串
     */
    fun truncate(input: String, maxLength: Int): String {
        return if (input.length > maxLength) {
            input.substring(0, maxLength) + "..."
        } else {
            input
        }
    }

    /**
     * 统计字符出现次数
     */
    fun countChar(input: String, char: Char): Int {
        return input.count { it == char }
    }

    /**
     * 判断是否包含数字
     */
    fun containsDigit(input: String): Boolean {
        return input.any { it.isDigit() }
    }

    /**
     * 判断是否包含字母
     */
    fun containsLetter(input: String): Boolean {
        return input.any { it.isLetter() }
    }

    /**
     * 首字母大写
     */
    fun capitalize(input: String): String {
        return input.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
