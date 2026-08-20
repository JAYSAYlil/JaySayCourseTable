package com.jaysay.coursetable.data.backup

import com.jaysay.coursetable.data.model.Course
import com.jaysay.coursetable.data.preferences.AppPreferences
import com.jaysay.coursetable.data.repository.TableData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

class EncryptedBackupCodecTest {
    private val course = Course(
        courseId = "secret-id", courseName = "保密课程", classNumber = "A1", department = "学院",
        credits = 2f, weeks = listOf(1, 2), dayOfWeek = 1, startPeriod = 1, endPeriod = 2,
        teacher = "真实教师", classroom = "秘密教室", courseType = "必修", courseCategory = "专业课",
        isOnline = false, assessmentMethod = "考试", notes = "敏感备注", seriesId = "series-1"
    )
    private val data = BackupData(listOf(TableData("测试课表", listOf(course))), AppPreferences())

    @Test fun encryptedBackupRoundTripsWithoutPlaintextLeak() {
        val password = "correct-password".toCharArray()
        val encoded = EncryptedBackupCodec.encode(data, password, SecureRandom(byteArrayOf(1, 2, 3)))
        assertTrue(EncryptedBackupCodec.isEncrypted(encoded))
        assertFalse(encoded.contains("保密课程"))
        assertFalse(encoded.contains("真实教师"))
        assertEquals(data.tables, EncryptedBackupCodec.decode(encoded, password).tables)
    }

    @Test fun wrongPasswordIsRejected() {
        val encoded = EncryptedBackupCodec.encode(data, "correct-password".toCharArray())
        val error = assertThrows(IllegalArgumentException::class.java) {
            EncryptedBackupCodec.decode(encoded, "wrong-password".toCharArray())
        }
        assertTrue(error.message.orEmpty().contains("密码错误"))
    }

    @Test fun shortPasswordIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            EncryptedBackupCodec.encode(data, "12345".toCharArray())
        }
    }
}
