package com.bitchat.android.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.*

/**
 * Educational content types supported by the app
 */
enum class EducationalContentType {
    PDF_DOCUMENT,
    VIDEO_LECTURE,
    AUDIO_LESSON,
    IMAGE_DIAGRAM,
    QUIZ_ASSESSMENT,
    TEXT_NOTES,
    PRESENTATION,
    ASSIGNMENT
}

/**
 * Educational subjects/categories for content organization
 */
enum class Subject {
    MATHEMATICS,
    SCIENCE_PHYSICS,
    SCIENCE_CHEMISTRY,
    SCIENCE_BIOLOGY,
    ENGLISH,
    HINDI,
    HISTORY,
    GEOGRAPHY,
    COMPUTER_SCIENCE,
    ECONOMICS,
    ARTS,
    VOCATIONAL_SKILLS,
    GENERAL_KNOWLEDGE,
    EXAM_PREPARATION
}

/**
 * Academic levels for content targeting
 */
enum class AcademicLevel {
    PRIMARY_1_5,      // Classes 1-5
    MIDDLE_6_8,       // Classes 6-8
    SECONDARY_9_10,   // Classes 9-10
    SENIOR_11_12,     // Classes 11-12
    UNDERGRADUATE,
    POSTGRADUATE,
    COMPETITIVE_EXAMS,
    SKILL_DEVELOPMENT
}

/**
 * Educational content metadata
 */
@Parcelize
data class EducationalContent(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val contentType: EducationalContentType,
    val subject: Subject,
    val academicLevel: AcademicLevel,
    val language: String = "en", // ISO language code (hi for Hindi, en for English)
    val fileSize: Long, // in bytes
    val filePath: String? = null, // local file path when downloaded
    val checksum: String, // for integrity verification
    val uploadedBy: String, // peer who shared this content
    val uploadDate: Date,
    val downloadCount: Int = 0,
    val rating: Float = 0.0f,
    val tags: List<String> = emptyList(), // additional searchable tags
    val duration: Long? = null, // for video/audio content in seconds
    val pageCount: Int? = null, // for PDF documents
    val prerequisites: List<String> = emptyList(), // required knowledge
    val difficulty: Int = 1, // 1-5 scale
    val isDownloaded: Boolean = false,
    val downloadProgress: Int = 0, // 0-100 percentage
    val lastAccessDate: Date? = null
) : Parcelable

/**
 * Study progress tracking for individual content
 */
@Parcelize
data class StudyProgress(
    val contentId: String,
    val userId: String, // peer ID
    val lastPosition: Long = 0, // page number for PDF, timestamp for video/audio
    val totalProgress: Float = 0.0f, // 0.0 to 1.0
    val completionDate: Date? = null,
    val studyTimeMinutes: Long = 0,
    val bookmarks: List<StudyBookmark> = emptyList(),
    val notes: List<StudyNote> = emptyList(),
    val quizScores: List<QuizResult> = emptyList()
) : Parcelable

/**
 * Bookmarks within educational content
 */
@Parcelize
data class StudyBookmark(
    val id: String = UUID.randomUUID().toString(),
    val position: Long, // page/timestamp
    val title: String,
    val note: String = "",
    val createdDate: Date = Date()
) : Parcelable

/**
 * Personal notes on educational content
 */
@Parcelize
data class StudyNote(
    val id: String = UUID.randomUUID().toString(),
    val position: Long, // page/timestamp where note was taken
    val content: String,
    val createdDate: Date = Date(),
    val lastModified: Date = Date()
) : Parcelable

/**
 * Quiz/assessment results
 */
@Parcelize
data class QuizResult(
    val quizId: String,
    val score: Int,
    val totalQuestions: Int,
    val completionDate: Date,
    val timeSpentMinutes: Int
) : Parcelable

/**
 * Educational content sharing request
 */
@Parcelize
data class ContentShareRequest(
    val requestId: String = UUID.randomUUID().toString(),
    val requesterId: String,
    val requesterName: String,
    val contentId: String,
    val contentTitle: String,
    val priority: Int = 1, // 1-5, higher for exam preparation
    val requestDate: Date = Date(),
    val reason: String = "" // why they need this content
) : Parcelable

/**
 * Extended BitchatMessage for educational content
 */
@Parcelize
data class EducationalMessage(
    val baseMessage: BitchatMessage,
    val educationalContent: EducationalContent? = null,
    val contentShareRequest: ContentShareRequest? = null,
    val studyGroupInvite: StudyGroupInvite? = null
) : Parcelable

/**
 * Study group collaboration
 */
@Parcelize
data class StudyGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val subject: Subject,
    val academicLevel: AcademicLevel,
    val description: String,
    val createdBy: String,
    val createdDate: Date = Date(),
    val members: List<String> = emptyList(), // peer IDs
    val isPublic: Boolean = true,
    val maxMembers: Int = 20,
    val language: String = "en"
) : Parcelable

/**
 * Study group invitation
 */
@Parcelize
data class StudyGroupInvite(
    val groupId: String,
    val groupName: String,
    val invitedBy: String,
    val inviteDate: Date = Date(),
    val message: String = ""
) : Parcelable
