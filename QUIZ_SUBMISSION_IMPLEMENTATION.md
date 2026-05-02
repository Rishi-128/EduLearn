# Quiz Submission Implementation

## Overview

Complete bidirectional quiz distribution system via mesh network that allows:

- Teachers to distribute quizzes to all students instantly
- Students to receive quizzes in real-time
- Students to submit completed quizzes back to teachers
- Teachers to see student scores and submissions immediately

## Architecture Flow

### Teacher → Student (Quiz Distribution)

```
Teacher creates quiz in SimpleEducationalScreen
    ↓
QuizDistributionService.distributeQuiz()
    ↓
Serializes quiz to JSON
    ↓
BluetoothMeshService.sendQuizDistribution(quizBytes)
    ↓
Creates QUIZ_DISTRIBUTION packet (0x32u)
    ↓
Broadcasts to mesh network
    ↓
PacketProcessor routes QUIZ_DISTRIBUTION packets
    ↓
MessageHandler.handleQuizDistribution()
    ↓
Calls delegate.onQuizReceived()
    ↓
BluetoothMeshService forwards to didReceiveQuiz()
    ↓
ChatViewModel.didReceiveQuiz()
    ↓
QuizDistributionService.handleReceivedQuiz()
    ↓
Parses JSON, updates _availableQuizzes StateFlow
    ↓
Student sees quiz in SimpleEducationalScreen
```

### Student → Teacher (Quiz Submission)

```
Student completes quiz and clicks Submit
    ↓
QuizDistributionService.submitQuiz(answers, score)
    ↓
Serializes submission to JSON
    ↓
BluetoothMeshService.sendQuizSubmission(submissionBytes)
    ↓
Creates QUIZ_SUBMISSION packet (0x33u)
    ↓
Broadcasts to mesh network
    ↓
PacketProcessor routes QUIZ_SUBMISSION packets
    ↓
MessageHandler.handleQuizSubmission()
    ↓
Calls delegate.onQuizSubmissionReceived()
    ↓
BluetoothMeshService forwards to didReceiveSubmission()
    ↓
ChatViewModel.didReceiveSubmission()
    ↓
QuizDistributionService.handleReceivedSubmission()
    ↓
Parses JSON, updates _quizSubmissions StateFlow
    ↓
Teacher sees submission with score in EnhancedTeacherDashboard
```

## Implementation Details

### 1. Message Types (BinaryProtocol.kt)

- `QUIZ_DISTRIBUTION = 0x32u` - Teacher → Students quiz broadcast
- `QUIZ_SUBMISSION = 0x33u` - Student → Teacher submission with answers/score

### 2. BluetoothMeshService.kt

Added methods:

- `sendQuizDistribution(ByteArray)` - Creates and broadcasts quiz packet
- `sendQuizSubmission(ByteArray)` - Creates and broadcasts submission packet

Updated delegate interface:

```kotlin
interface BluetoothMeshDelegate {
    fun didReceiveQuiz(quizJson: String, senderPeerID: String)
    fun didReceiveSubmission(submissionJson: String, senderPeerID: String)
}
```

### 3. MessageHandler.kt

Added handlers:

- `handleQuizDistribution()` - Extracts quiz JSON from packet
- `handleQuizSubmission()` - Extracts submission JSON from packet

Updated delegate interface:

```kotlin
interface MessageHandlerDelegate {
    fun onQuizReceived(quizJson: String, senderPeerID: String)
    fun onQuizSubmissionReceived(submissionJson: String, senderPeerID: String)
}
```

### 4. PacketProcessor.kt

Added routing:

- `MessageType.QUIZ_DISTRIBUTION → handleQuizDistribution()`
- `MessageType.QUIZ_SUBMISSION → handleQuizSubmission()`

Updated delegate interface:

```kotlin
interface PacketProcessorDelegate {
    fun handleQuizDistribution(routed: RoutedPacket)
    fun handleQuizSubmission(routed: RoutedPacket)
}
```

### 5. QuizDistributionService.kt

Key methods:

- `distributeQuiz()` - Sends quiz via meshService.sendQuizDistribution()
- `submitQuiz()` - Sends submission via meshService.sendQuizSubmission()
- `handleReceivedQuiz()` - Processes incoming quizzes, updates StateFlow
- `handleReceivedSubmission()` - Processes incoming submissions, updates StateFlow

StateFlows:

- `_availableQuizzes` - List of quizzes available to student
- `_quizSubmissions` - List of submissions received by teacher

### 6. ChatViewModel.kt

Implements BluetoothMeshDelegate:

```kotlin
override fun didReceiveQuiz(quizJson: String, senderPeerID: String) {
    quizDistributionService.handleReceivedQuiz(quizJson)
}

override fun didReceiveSubmission(submissionJson: String, senderPeerID: String) {
    quizDistributionService.handleReceivedSubmission(submissionJson)
}
```

## Data Format

### Quiz Distribution Packet

```json
{
  "quizId": "uuid",
  "title": "Quiz Title",
  "questions": [
    {
      "text": "Question?",
      "options": ["A", "B", "C", "D"],
      "correctAnswer": 0
    }
  ],
  "timeLimit": 300,
  "distributedAt": 1234567890,
  "distributedBy": "teacherPeerID"
}
```

### Quiz Submission Packet

```json
{
  "quizId": "uuid",
  "studentId": "studentPeerID",
  "answers": [0, 2, 1],
  "score": 85,
  "completedAt": 1234567890
}
```

## Security

- All packets go through mesh network security (Noise Protocol encryption)
- Quiz distribution uses broadcast (reaches all connected peers)
- Quiz submissions use broadcast (allows teacher on any node to receive)
- Duplicate submissions filtered by quiz ID + student ID combination

## Performance

- Quizzes serialized to JSON (~1-5KB for typical quiz)
- Submissions serialized to JSON (~100-500 bytes)
- GZIP compression applied automatically by mesh layer
- No file chunking needed for typical quiz sizes

## Testing Flow

1. Teacher creates quiz with multiple questions
2. Teacher clicks "Distribute Quiz" → broadcasts via mesh
3. Students see quiz appear in "Available Quizzes" section
4. Student opens quiz, answers questions
5. Student clicks "Submit" → sends submission via mesh
6. Teacher sees submission appear with:
   - Student name
   - Score/percentage
   - Completion time
   - Answers (optional)

## Future Enhancements

- Quiz expiration/deadline support
- Multiple submissions handling
- Answer key display after deadline
- Quiz analytics dashboard
- Offline quiz caching
- Quiz result export
