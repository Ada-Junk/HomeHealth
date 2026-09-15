package com.example.homehealth.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 敏感字符串的本地加密（目前用于 API Key）。
 *
 * 密钥由 Android Keystore 托管，**不落盘、不可导出**：应用只持有句柄，密钥材料始终留在系统
 * Keystore 中。因此即使应用私有目录被完整读走，没有该设备的 Keystore 也解不开密文。
 *
 * 与「allowBackup=false」的分工：后者挡住的是备份导出通道（adb backup / 云备份），
 * 本类挡住的是 root 设备上直接读文件。二者是不同威胁面，不能相互替代。
 *
 * 存储格式：`enc:v1:<base64(iv)>:<base64(ciphertext)>`
 * - 前缀用于识别历史遗留的明文值，从而支持一次性平滑迁移；
 * - IV 每次加密随机生成 —— GCM 下复用 IV 会导致灾难性失密，绝不能固定 IV。
 *
 * 加解密失败一律返回 null，由调用方决定降级策略。本项目选择「读失败视为未配置」，
 * 绝不让 Keystore 异常把应用拖崩。
 */
object SecretStore {

    private const val TAG = "SecretStore"
    private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    private const val KEY_ALIAS = "homehealth_secret_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BITS = 128
    private const val PREFIX = "enc:v1:"

    /** 是否为加密存储的值（false 表示历史遗留的明文，需要迁移） */
    fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)

    /** 加密；返回 null 表示当前环境不可用（调用方需降级处理，不要抛给用户） */
    fun encrypt(plain: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        PREFIX + Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }.onFailure {
        // 只记异常类型：异常消息可能夹带输入片段
        Log.w(TAG, "加密失败：${it.javaClass.simpleName}")
    }.getOrNull()

    /** 解密；返回 null 表示密文不可用（Keystore 被清空 / 恢复到了另一台设备 / 数据损坏） */
    fun decrypt(stored: String): String? = runCatching {
        val body = stored.removePrefix(PREFIX)
        val separator = body.indexOf(':')
        require(separator > 0) { "密文格式非法" }
        val iv = Base64.decode(body.substring(0, separator), Base64.NO_WRAP)
        val data = Base64.decode(body.substring(separator + 1), Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
        String(cipher.doFinal(data), Charsets.UTF_8)
    }.onFailure {
        Log.w(TAG, "解密失败：${it.javaClass.simpleName}")
    }.getOrNull()

    /** 密钥访问加锁：并发首次调用若各自生成密钥，后生成的会覆盖前一个，导致先写入的密文永久无法解开 */
    private val lock = Any()

    /** 取（或首次生成）Keystore 中的 AES 密钥 */
    private fun secretKey(): SecretKey = synchronized(lock) {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)
            ?.let { return@synchronized it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // 刻意不要求用户认证：一旦要求，用户改锁屏或移除锁屏都会让密钥失效，
                // API Key 会突然读不出来 —— 那属于"安全措施制造可用性事故"
                .setUserAuthenticationRequired(false)
                .setKeySize(256)
                .build()
        )
        generator.generateKey()
    }
}
