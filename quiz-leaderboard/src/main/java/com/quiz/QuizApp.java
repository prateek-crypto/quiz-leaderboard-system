package com.quiz;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/**
 * QuizApp - Polls a quiz API, deduplicates event data,
 * builds a leaderboard, and submits the final result.
 *
 * Author: Aryan Gupta
 * Registration No: 2024CS101
 */
public class QuizApp {

    // --- Configuration ---
    private static final String REG_NO       = "2024CS101";
    private static final String BASE_URL     = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";
    private static final int    TOTAL_POLLS  = 10;
    private static final int    DELAY_MS     = 5000; // 5 seconds between polls

    // Holds unique events: key = roundId + "|" + participant
    private static final Set<String>              seenEvents    = new HashSet<>();
    private static final Map<String, Integer>     scoreboard    = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        System.out.println("=== Quiz Leaderboard App Started ===");
        System.out.println("Registration Number : " + REG_NO);
        System.out.println("Total Polls         : " + TOTAL_POLLS);
        System.out.println("Delay between polls : " + (DELAY_MS / 1000) + " seconds");
        System.out.println("=====================================\n");

        // ---- Step 1: Poll the API 10 times ----
        for (int poll = 0; poll < TOTAL_POLLS; poll++) {

            System.out.println("[Poll " + poll + "] Sending request...");

            String url = BASE_URL + "/quiz/messages?regNo=" + REG_NO + "&poll=" + poll;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            System.out.println("[Poll " + poll + "] Status: " + response.statusCode());

            // ---- Step 2 & 3: Parse and deduplicate events ----
            parseAndProcess(body, poll);

            // Wait before next poll (skip delay after last poll)
            if (poll < TOTAL_POLLS - 1) {
                System.out.println("[Poll " + poll + "] Waiting " + (DELAY_MS / 1000) + "s before next poll...\n");
                try {
                    Thread.sleep(DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        // ---- Step 4 & 5: Sort leaderboard by total score descending ----
        List<Map.Entry<String, Integer>> leaderboardList = new ArrayList<>(scoreboard.entrySet());
        leaderboardList.sort((a, b) -> b.getValue() - a.getValue());

        // ---- Step 6: Compute total score ----
        int totalScore = 0;
        for (Map.Entry<String, Integer> entry : leaderboardList) {
            totalScore += entry.getValue();
        }

        System.out.println("\n=== Final Leaderboard ===");
        int rank = 1;
        for (Map.Entry<String, Integer> entry : leaderboardList) {
            System.out.println(rank++ + ". " + entry.getKey() + " -> " + entry.getValue() + " pts");
        }
        System.out.println("Combined Total Score: " + totalScore);
        System.out.println("=========================\n");

        // ---- Step 7: Submit leaderboard once ----
        submitLeaderboard(client, leaderboardList);
    }

    /**
     * Parses the raw JSON response string manually (no external libraries)
     * and processes each event — skipping duplicates.
     */
    private static void parseAndProcess(String json, int pollIndex) {

        // Pull out the events array using simple string search
        int eventsStart = json.indexOf("\"events\"");
        if (eventsStart == -1) {
            System.out.println("  [!] No events found in poll " + pollIndex);
            return;
        }

        // Find all roundId / participant / score triplets
        // Format: {"roundId":"R1","participant":"Alice","score":10}
        int idx = eventsStart;
        int newEvents   = 0;
        int skipEvents  = 0;

        while (true) {
            int roundStart = json.indexOf("\"roundId\"", idx);
            if (roundStart == -1) break;

            String roundId      = extractStringValue(json, roundStart);
            int participantPos  = json.indexOf("\"participant\"", roundStart);
            String participant  = extractStringValue(json, participantPos);
            int scorePos        = json.indexOf("\"score\"", participantPos);
            int score           = extractIntValue(json, scorePos);

            // Unique key for deduplication
            String eventKey = roundId + "|" + participant;

            if (!seenEvents.contains(eventKey)) {
                seenEvents.add(eventKey);
                scoreboard.merge(participant, score, Integer::sum);
                System.out.println("  [NEW]  " + participant + " +" + score + "  (round=" + roundId + ")");
                newEvents++;
            } else {
                System.out.println("  [DUP]  " + participant + " skipped   (round=" + roundId + ")");
                skipEvents++;
            }

            // Move index past this event block
            idx = scorePos + 1;
        }

        System.out.println("  => Added: " + newEvents + "  Skipped (duplicates): " + skipEvents);
    }

    /**
     * Reads the string value right after a JSON key like "roundId":"VALUE"
     */
    private static String extractStringValue(String json, int keyStart) {
        int colon      = json.indexOf(":", keyStart);
        int quoteOpen  = json.indexOf("\"", colon + 1);
        int quoteClose = json.indexOf("\"", quoteOpen + 1);
        return json.substring(quoteOpen + 1, quoteClose);
    }

    /**
     * Reads the integer value right after a JSON key like "score":VALUE
     */
    private static int extractIntValue(String json, int keyStart) {
        int colon = json.indexOf(":", keyStart);
        int numStart = colon + 1;
        // skip whitespace
        while (numStart < json.length() && (json.charAt(numStart) == ' ' || json.charAt(numStart) == '\n')) {
            numStart++;
        }
        int numEnd = numStart;
        while (numEnd < json.length() && Character.isDigit(json.charAt(numEnd))) {
            numEnd++;
        }
        return Integer.parseInt(json.substring(numStart, numEnd));
    }

    /**
     * Builds and sends the POST /quiz/submit request with the leaderboard.
     */
    private static void submitLeaderboard(HttpClient client,
            List<Map.Entry<String, Integer>> leaderboardList) throws Exception {

        // Build JSON body manually
        StringBuilder jsonBody = new StringBuilder();
        jsonBody.append("{");
        jsonBody.append("\"regNo\":\"").append(REG_NO).append("\",");
        jsonBody.append("\"leaderboard\":[");

        for (int i = 0; i < leaderboardList.size(); i++) {
            Map.Entry<String, Integer> entry = leaderboardList.get(i);
            jsonBody.append("{");
            jsonBody.append("\"participant\":\"").append(entry.getKey()).append("\",");
            jsonBody.append("\"totalScore\":").append(entry.getValue());
            jsonBody.append("}");
            if (i < leaderboardList.size() - 1) jsonBody.append(",");
        }

        jsonBody.append("]}");

        System.out.println("[Submit] Sending leaderboard...");
        System.out.println("[Submit] Payload: " + jsonBody);

        HttpRequest submitRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/quiz/submit"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody.toString()))
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> submitResponse = client.send(submitRequest,
                HttpResponse.BodyHandlers.ofString());

        System.out.println("\n=== Submit Response ===");
        System.out.println("HTTP Status : " + submitResponse.statusCode());
        System.out.println("Response    : " + submitResponse.body());
        System.out.println("======================");
    }
}
