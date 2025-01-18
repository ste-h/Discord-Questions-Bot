package org.example;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
	private static final String DATABASE_URL = "jdbc:sqlite:questions.db";

	private static class SingletonHelper {
		private static final DatabaseManager INSTANCE = new DatabaseManager();
	}
	public static DatabaseManager getInstance() {
		return SingletonHelper.INSTANCE;
	}
	private DatabaseManager() {
		createQuestionsTable();
	}

	private void createQuestionsTable()
	{
		String sql = "CREATE TABLE IF NOT EXISTS questions ("
					 + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
					 + "message_id TEXT NOT NULL UNIQUE,"
					 + "author_id TEXT NOT NULL,"
					 + "reposted_message_id TEXT,"
					 + "author_name TEXT NOT NULL,"
					 + "author_avatar_url TEXT NOT NULL,"
					 + "message_content TEXT NOT NULL,"
					 + "status TEXT NOT NULL,"
					 + "timestamp TEXT DEFAULT CURRENT_TIMESTAMP"
					 + ");";
		try (Connection conn = getConnection(); Statement stmt = conn.createStatement())
		{
			stmt.execute(sql);
			System.out.println("Table 'questions' created or already exists.");
		}
		catch (SQLException e)
		{
			System.err.println("Error creating table: " + e.getMessage());
		}
	}

	public Connection getConnection() throws SQLException
	{
		return DriverManager.getConnection(DATABASE_URL);
	}

	// Insert a new question into the database
	public int insertQuestion(Question question)
	{
		String sql = "INSERT INTO questions (message_id, author_id, author_name, author_avatar_url, message_content, status) VALUES (?, ?, ?, ?, ?, ?)";
		try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS))
		{
			pstmt.setString(1, question.getMessageId());
			pstmt.setString(2, question.getAuthorId());
			pstmt.setString(3, question.getAuthorName());
			pstmt.setString(4, question.getAuthorAvatarUrl());
			pstmt.setString(5, question.getMessageContent());
			pstmt.setString(6, question.getStatus());
			pstmt.executeUpdate();

			ResultSet generatedKeys = pstmt.getGeneratedKeys();
			if (generatedKeys.next())
			{
				int questionNumber = generatedKeys.getInt(1);
				System.out.println("Question inserted with ID: " + questionNumber);
				return questionNumber;
			}
		}
		catch (SQLException e)
		{
			System.err.println("Error inserting question: " + e.getMessage());
		}
		return -1;
	}

	// Update the status of a question
	public boolean updateQuestionStatus(String messageId, String newStatus)
	{
		String sql = "UPDATE questions SET status = ? WHERE message_id = ?";
		try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql))
		{
			pstmt.setString(1, newStatus);
			pstmt.setString(2, messageId);
			int affectedRows = pstmt.executeUpdate();
			System.out.println("Updated status for message_id: " + messageId + " to " + newStatus);
			return affectedRows > 0;
		}
		catch (SQLException e)
		{
			System.err.println("Error updating question status: " + e.getMessage());
			return false;
		}
	}

	// Retrieve a question by its question number
	public Question getQuestionByNumber(int questionNumber) {
		String sql = "SELECT * FROM questions WHERE id = ?";
		try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setInt(1, questionNumber);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next()) {
				return extractQuestionFromResultSet(rs);
			}
		} catch (SQLException e) {
			System.err.println("Error retrieving question by number: " + e.getMessage());
		}
		return null;
	}

	// Retrieve a question by its original message ID
	public Question getQuestionByMessageId(String messageId)
	{
		String sql = "SELECT * FROM questions WHERE message_id = ?";
		try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql))
		{
			pstmt.setString(1, messageId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next())
			{
				return extractQuestionFromResultSet(rs);
			}
		}
		catch (SQLException e)
		{
			System.err.println("Error retrieving question: " + e.getMessage());
		}
		return null;
	}

	// Retrieve a question by its reposted message ID
	public Question getQuestionByRepostedMessageId(String repostedMessageId)
	{
		String sql = "SELECT * FROM questions WHERE reposted_message_id = ?";
		try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql))
		{
			pstmt.setString(1, repostedMessageId);
			ResultSet rs = pstmt.executeQuery();
			if (rs.next())
			{
				return extractQuestionFromResultSet(rs);
			}
		}
		catch (SQLException e)
		{
			System.err.println("Error retrieving question: " + e.getMessage());
		}
		return null;
	}

	// Retrieve all questions with a specific status
	public List<Question> getQuestionsByStatus(String status, int limit)
	{
		String sql = "SELECT * FROM questions WHERE status = ? ORDER BY id DESC LIMIT ?";
		List<Question> questions = new ArrayList<>();
		try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql))
		{
			pstmt.setString(1, status);
			pstmt.setInt(2, limit);
			ResultSet rs = pstmt.executeQuery();
			while (rs.next())
			{
				questions.add(extractQuestionFromResultSet(rs));
			}
		}
		catch (SQLException e)
		{
			System.err.println("Error retrieving questions by status: " + e.getMessage());
		}
		return questions;
	}

	private Question extractQuestionFromResultSet(ResultSet rs) throws SQLException {
		int id = rs.getInt("id");
		String messageId = rs.getString("message_id");
		String authorId = rs.getString("author_id");
		String authorName = rs.getString("author_name");
		String authorAvatarUrl = rs.getString("author_avatar_url");
		String messageContent = rs.getString("message_content");
		String status = rs.getString("status");
		String repostedMessageId = rs.getString("reposted_message_id");
		String timestamp = rs.getString("timestamp");

		Question question = new Question(messageId, authorId, authorName, authorAvatarUrl, messageContent, status);
		question.setId(id);
		question.setRepostedMessageId(repostedMessageId);

		return question;
	}

	public boolean updateRepostedMessageId(String originalMessageId, String repostedMessageId) {
		String sql = "UPDATE questions SET reposted_message_id = ? WHERE message_id = ?";
		try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, repostedMessageId);
			pstmt.setString(2, originalMessageId);
			int affectedRows = pstmt.executeUpdate();
			System.out.println("Updated reposted_message_id for message_id: " + originalMessageId);
			return affectedRows > 0;
		} catch (SQLException e) {
			System.err.println("Error updating reposted_message_id: " + e.getMessage());
			return false;
		}
	}


}