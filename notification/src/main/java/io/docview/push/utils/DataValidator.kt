package io.docview.push.utils

/**
 * 数据验证工具
 */
object DataValidator {

    /**
     * 验证邮箱格式
     */
    fun isValidEmail(email: String): Boolean {
        val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
        return email.matches(emailPattern.toRegex())
    }

    /**
     * 验证手机号格式
     */
    fun isValidPhone(phone: String): Boolean {
        return phone.matches("^1[3-9]\\d{9}$".toRegex())
    }

    /**
     * 验证URL格式
     */
    fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    /**
     * 验证数字范围
     */
    fun isInRange(value: Int, min: Int, max: Int): Boolean {
        return value in min..max
    }

    /**
     * 验证非空
     */
    fun isNotEmpty(value: String?): Boolean {
        return !value.isNullOrEmpty()
    }

    /**
     * 验证长度
     */
    fun isValidLength(value: String, minLength: Int, maxLength: Int): Boolean {
        return value.length in minLength..maxLength
    }

    /**
     * 验证是否为数字
     */
    fun isNumeric(value: String): Boolean {
        return value.all { it.isDigit() }
    }

    /**
     * 验证是否为字母
     */
    fun isAlpha(value: String): Boolean {
        return value.all { it.isLetter() }
    }
}
