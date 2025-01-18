package org.example;

import java.time.temporal.Temporal;

public class Question {
	private int id;
	private int questionNumber;
	private String messageId;
	private String authorId;
	private String authorName;
	private String authorAvatarUrl;
	private String messageContent;
	private String status;
	private String repostedMessageId;
	private Temporal timestamp;

	public Question(String messageId, String authorId, String authorName, String authorAvatarUrl, String messageContent, String status) {
		this.messageId = messageId;
		this.authorId = authorId;
		this.authorName = authorName;
		this.authorAvatarUrl = authorAvatarUrl;
		this.messageContent = messageContent;
		this.status = status;
	}

	// Getters and setters
	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public int getQuestionNumber() {
		return questionNumber;
	}

	public void setQuestionNumber(int questionNumber) {
		this.questionNumber = questionNumber;
	}

	public String getMessageId() {
		return messageId;
	}

	public String getAuthorId() {
		return authorId;
	}

	public String getAuthorName() {
		return authorName;
	}

	public String getAuthorAvatarUrl() {
		return authorAvatarUrl;
	}

	public String getMessageContent() {
		return messageContent;
	}

	public String getStatus() {
		return status;
	}

	public String getRepostedMessageId() {
		return repostedMessageId;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public void setRepostedMessageId(String messageId) {
		this.repostedMessageId = messageId;
	}

	public void setTimestamp(Temporal timestamp) {
		this.timestamp = timestamp;
	}
}

