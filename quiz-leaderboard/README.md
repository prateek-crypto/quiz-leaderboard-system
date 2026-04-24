# Quiz Leaderboard — SRM Backend Assignment

A Java application that polls a quiz API, handles duplicate events correctly, builds a leaderboard and submits the final result.

---

## What this does

The program:
1. Calls the quiz API **10 times** (once every 5 seconds) with poll values 0–9
2. Collects all the score events from each response
3. **Skips duplicate events** — same round + same participant = already counted
4. Adds up the real scores per participant
5. Sorts participants by total score (highest first)
6. Submits the leaderboard **once** to the submit endpoint

---

## Why deduplication matters

The API sometimes sends the same event in multiple polls. If you blindly add every score you see, your final total will be wrong.

**Wrong approach:**
```
Poll 0 → Alice gets 10 pts  → running total: 10
Poll 3 → Alice gets 10 pts  → running total: 20  ← duplicate, should be skipped!
```

**What this code does:**
```
Poll 0 → Alice gets 10 pts  → new event, add it  → running total: 10
Poll 3 → Alice gets 10 pts  → already seen, skip → running total: 10  ✓
```

The key used for deduplication is: `roundId + "|" + participant`

---

## Project structure

```
quiz-leaderboard/
├── pom.xml                          ← Maven build config
├── README.md                        ← this file
└── src/
    └── main/
        └── java/
            └── com/
                └── quiz/
                    └── QuizApp.java ← all the logic lives here
```

---

## Requirements

- Java 11 or higher
- Maven 3.6+

Check your versions:
```bash
java -version
mvn -version
```

---

## How to run

### 1. Clone / download the project

```bash
git clone https://github.com/your-username/quiz-leaderboard.git
cd quiz-leaderboard
```

### 2. Build it

```bash
mvn clean package
```

This creates `target/quiz-leaderboard.jar`

### 3. Run it

```bash
java -jar target/quiz-leaderboard.jar
```

The program will run for about **45–50 seconds** (10 polls × 5s delay). You'll see live output like:

```
=== Quiz Leaderboard App Started ===
Registration Number : 2024CS101
Total Polls         : 10
Delay between polls : 5 seconds
=====================================

[Poll 0] Sending request...
[Poll 0] Status: 200
  [NEW]  Alice +10  (round=R1)
  [NEW]  Bob +20    (round=R1)
  => Added: 2  Skipped (duplicates): 0
[Poll 0] Waiting 5s before next poll...

[Poll 1] Sending request...
  [DUP]  Alice skipped   (round=R1)
  [NEW]  Charlie +15     (round=R2)
  ...

=== Final Leaderboard ===
1. Bob     -> 120 pts
2. Alice   -> 100 pts
3. Charlie -> 80 pts
Combined Total Score: 300
=========================

[Submit] Sending leaderboard...
=== Submit Response ===
HTTP Status : 200
Response    : {"isCorrect":true,"isIdempotent":true,...}
```

---

## No external libraries

This project uses **zero third-party dependencies**. Everything — HTTP calls, JSON parsing, deduplication — is done with plain Java standard library (`java.net.http`, `java.util`). This keeps the build simple and easy to understand.

---

## Configuration

If you want to change the registration number or base URL, open `QuizApp.java` and edit the constants at the top:

```java
private static final String REG_NO   = "2024CS101";
private static final String BASE_URL = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";
```

---

## Author

Aryan Gupta — SRM Institute of Science and Technology  
Registration No: 2024CS101
