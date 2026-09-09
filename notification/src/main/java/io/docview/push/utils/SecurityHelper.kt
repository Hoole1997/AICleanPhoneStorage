package io.docview.push.utils

import android.util.Base64
import java.security.MessageDigest
import java.util.Random
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 安全辅助工具类
 * 提供加密解密等安全相关功能
 */
object SecurityHelper {

    private const val ALGORITHM = "AES"
    private val random = Random()

    /**
     * 生成随机字符串
     */
    fun generateRandomString(length: Int = 16): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }

    /**
     * 计算MD5哈希
     */
    fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Base64编码
     */
    fun encodeBase64(input: String): String {
        return Base64.encodeToString(input.toByteArray(), Base64.DEFAULT)
    }

    /**
     * Base64解码
     */
    fun decodeBase64(input: String): String {
        return String(Base64.decode(input, Base64.DEFAULT))
    }

    /**
     * 简单加密
     */
    fun encrypt(data: String, key: String): String {
        return try {
            val secretKey = SecretKeySpec(key.padEnd(16, '0').take(16).toByteArray(), ALGORITHM)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val encrypted = cipher.doFinal(data.toByteArray())
            Base64.encodeToString(encrypted, Base64.DEFAULT)
        } catch (e: Exception) {
            data
        }
    }

    /**
     * 简单解密
     */
    fun decrypt(data: String, key: String): String {
        return try {
            val secretKey = SecretKeySpec(key.padEnd(16, '0').take(16).toByteArray(), ALGORITHM)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            val decrypted = cipher.doFinal(Base64.decode(data, Base64.DEFAULT))
            String(decrypted)
        } catch (e: Exception) {
            data
        }
    }

    /**
     * 验证字符串合法性
     */
    fun isValidString(input: String?): Boolean {
        return !input.isNullOrBlank() && input.length > 0
    }

    /**
     * 生成时间戳
     */
    fun generateTimestamp(): Long {
        return System.currentTimeMillis()
    }

    /**
     * 混淆字符串
     */
    fun obfuscate(input: String): String {
        return input.reversed().map {
            if (it.isLetter()) (it.code xor 5).toChar() else it
        }.joinToString("")
    }
}
