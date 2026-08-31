package com.pollbot.model;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a single question within a poll.
 */
public class Question {

    private final String text;
    private final List<String> options; // 2-4 options
    private final Map<Integer, Integer> votes; // optionIndex -> vote count
    private final Set<Long> answeredBy; // chatIds who already answered

    public Question(String text, List<String> options) {
        if (options.size() < 2 || options.size() > 4) {
            throw new IllegalArgumentException("A question must have 2-4 options, got " + options.size());
        }
        this.text = text;
        this.options = new ArrayList<>(options);
        this.votes = new ConcurrentHashMap<>();
        this.answeredBy = ConcurrentHashMap.newKeySet();

        // Initialize vote counts to 0
        for (int i = 0; i < options.size(); i++) {
            votes.put(i, 0);
        }
    }

    public String getText() {
        return text;
    }

    public List<String> getOptions() {
        return Collections.unmodifiableList(options);
    }

    public Map<Integer, Integer> getVotes() {
        return Collections.unmodifiableMap(votes);
    }

    /**
     * Records a vote for this question.
     * @return true if the vote was recorded, false if the user already answered.
     */
    public boolean recordVote(long chatId, int optionIndex) {
        if (answeredBy.contains(chatId)) {
            return false; // Already answered
        }
        if (optionIndex < 0 || optionIndex >= options.size()) {
            return false; // Invalid option
        }
        answeredBy.add(chatId);
        votes.merge(optionIndex, 1, Integer::sum);
        return true;
    }

    public boolean hasAnswered(long chatId) {
        return answeredBy.contains(chatId);
    }

    public int getTotalVotes() {
        return votes.values().stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * Returns options sorted by vote count descending.
     */
    public List<Map.Entry<String, Integer>> getResultsSorted() {
        List<Map.Entry<String, Integer>> results = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            results.add(Map.entry(options.get(i), votes.getOrDefault(i, 0)));
        }
        results.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return results;
    }
}
