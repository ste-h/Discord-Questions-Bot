package org.example;

public class Question {
	private final String messageId;
	private final String authorId;
	private final String authorName;
	private final String authorAvatarUrl;
	private final String messageContent;
	private final String status;
	private final int questionNumber;

	public Question(String messageId, String authorId, String authorName, String authorAvatarUrl, String messageContent, String status, int questionNumber) {
		this.messageId = messageId;
		this.authorId = authorId;
		this.authorName = authorName;
		this.authorAvatarUrl = authorAvatarUrl;
		this.messageContent = messageContent;
		this.status = status;
		this.questionNumber = questionNumber;
	}

	// Getters for the fields
	public String getMessageId() { return messageId; }
	public String getAuthorId() { return authorId; }
	public String getAuthorName() { return authorName; }
	public String getAuthorAvatarUrl() { return authorAvatarUrl; }
	public String getMessageContent() { return messageContent; }
	public String getStatus() { return status; }
	public int getQuestionNumber() { return questionNumber; }
}

