package org.example;

import java.awt.Color;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class MessageLogger extends ListenerAdapter
{
	public static final Emoji GREEN_TICK = Emoji.fromUnicode("U+2705");
	public static final Emoji RED_CROSS = Emoji.fromUnicode("U+274C");
	public static final Emoji QUESTION_MARK = Emoji.fromUnicode("U+2753");
	public static final String SUBMISSION_CHANNEL_ID = "1291263144701722696";
	public static final String DISCUSSION_CHANNEL_ID = "1292697957422071881";
	public static final String APPROVED_CHANNEL_ID = "1292697980104740935";
	public static final String QUESTION_MARK_URL = "https://imgur.com/t9rjCIu.png";
	public static final String RED_CROSS_URL = "https://imgur.com/F6dcp2p.png";
	public static final String GREEN_TICK_URL = "https://imgur.com/gbFWmXQ.png";

	Message askedQuestion;

	public static void main(String[] args)
	{
		String url = "jdbc:sqlite:questions.db";

		String sql = "CREATE TABLE IF NOT EXISTS questions (\n"
					 + "    id INTEGER PRIMARY KEY AUTOINCREMENT,\n"
					 + "    message_id TEXT NOT NULL UNIQUE,\n"
					 + "    author_id TEXT NOT NULL,\n"
					 + "    reposted_message_id TEXT,\n"
					 + "    author_name TEXT NOT NULL,\n"
					 + "    author_avatar_url TEXT NOT NULL,\n"
					 + "    message_content TEXT NOT NULL, \n"
					 + "    status TEXT NOT NULL,\n"
					 + "    timestamp TEXT DEFAULT CURRENT_TIMESTAMP\n"
					 + ");";

		try (Connection conn = DriverManager.getConnection(url);
			 Statement stmt = conn.createStatement())
		{
			// Create table if it doesn't exist
			stmt.execute(sql);
			System.out.println("Table 'questions' created or already exists.");
		}
		catch (SQLException e)
		{
			System.out.println(e.getMessage());
		}

		String token = System.getenv("TOKEN");
		if (token == null || token.isEmpty())
		{
			System.err.println("Error: The TOKEN environment variable is not set.");
			System.exit(1);
		}

		EnumSet<GatewayIntent> intents = EnumSet.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.DIRECT_MESSAGES, GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MESSAGE_REACTIONS, GatewayIntent.DIRECT_MESSAGE_REACTIONS);

		try
		{
			JDA jda = JDABuilder.createLight(token, intents).addEventListeners(new MessageLogger()).setActivity(Activity.watching("your messages")).build();

			jda.getRestPing().queue(ping ->
				// shows ping in milliseconds
				System.out.println("Logged in with ping: " + ping));

			jda.awaitReady();

			System.out.println("Guilds: " + jda.getGuildCache().size());
		}
		catch (InterruptedException e)
		{
			// Thrown if the awaitReady() call is interrupted
			e.printStackTrace();
		}
	}

	// Extract user information and insert it into the database
	@Override
	public void onMessageReceived(@Nonnull MessageReceivedEvent event)
	{
		String channelId = event.getGuildChannel().getId();

		if (event.isFromGuild() && channelId.equals(SUBMISSION_CHANNEL_ID))
		{
			String messageId = event.getMessage().getId();
			String authorId = event.getAuthor().getId();
			String authorName = event.getAuthor().getName();
			String authorAvatarUrl = event.getAuthor().getEffectiveAvatarUrl();
			String messageContent = event.getMessage().getContentRaw();
			String status = "submitted";

			String sqlInsert = "INSERT INTO questions (message_id, author_id, author_name, author_avatar_url, message_content, status) VALUES (?, ?, ?, ?, ?, ?)";

			try (Connection conn = DriverManager.getConnection("jdbc:sqlite:questions.db");
				 PreparedStatement pstmt = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS))
			{
				pstmt.setString(1, messageId);
				pstmt.setString(2, authorId);
				pstmt.setString(3, authorName);
				pstmt.setString(4, authorAvatarUrl);
				pstmt.setString(5, messageContent);
				pstmt.setString(6, status);
				pstmt.executeUpdate();

				// Retrieve the generated id (question number)
				ResultSet generatedKeys = pstmt.getGeneratedKeys();
				if (generatedKeys.next()) {
					int questionNumber = generatedKeys.getInt(1);
					System.out.println("Message inserted into database with author details, question number: " + questionNumber);

					// Create Question object
					Question question = new Question(messageId, authorId, authorName, authorAvatarUrl, messageContent, status, questionNumber);
					transferQuestionToDiscussion(event, question);
				} else {
					System.err.println("Failed to retrieve question number.");
				}
			}
			catch (SQLException e)
			{
				System.err.println("Error inserting message: " + e.getMessage());
			}

			// Delete the original message
			event.getMessage().delete().queue();
		}
	}

	private void updateQuestionStatus(String repostedMessageId, String status)
	{
		String sqlUpdate = "UPDATE questions SET status = ? WHERE reposted_message_id = ?";

		try (Connection conn = DriverManager.getConnection("jdbc:sqlite:questions.db");
			 PreparedStatement pstmt = conn.prepareStatement(sqlUpdate))
		{
			pstmt.setString(1, status);
			pstmt.setString(2, repostedMessageId);
			pstmt.executeUpdate();
			System.out.println("Updated the status to '" + status + "' in the database for reposted_message_id: " + repostedMessageId);
		}
		catch (SQLException e)
		{
			System.err.println("Error updating status: " + e.getMessage());
		}
	}

	private void updateMessageThumbnail(Message message, String thumbnailUrl)
	{
		if (!message.getEmbeds().isEmpty())
		{
			MessageEmbed oldEmbed = message.getEmbeds().get(0);
			EmbedBuilder embedBuilder = new EmbedBuilder(oldEmbed);

			embedBuilder.setThumbnail(thumbnailUrl);

			message.editMessageEmbeds(embedBuilder.build()).queue(
				success -> System.out.println("Thumbnail updated successfully!"),
				error -> System.err.println("Failed to update thumbnail: " + error.getMessage())
			);
		}
		else
		{
			System.err.println("No embed found in the message to edit.");
		}
	}

	public void onMessageReactionAdd(@Nonnull MessageReactionAddEvent event)
	{
		if (event.getUser().isBot())
		{
			return;
		}

		Emoji emoji = event.getReaction().getEmoji();
		User contributorUser = event.getUser();

		// Retrieve the message that was reacted to
		event.retrieveMessage().queue(message -> {
			String repostedMessageId = message.getId();
			TextChannel approvedChannel = event.getGuild().getTextChannelById(APPROVED_CHANNEL_ID);

			if (emoji.equals(GREEN_TICK))
			{
				// Repost the question to the approved channel
				repostQuestionFromRepostedMessageId(repostedMessageId, contributorUser, approvedChannel);

				// Update the embed to have the green tick thumbnail
				updateMessageThumbnail(message, GREEN_TICK_URL);

				// Update the status in the database
				updateQuestionStatus(repostedMessageId, "approved");

				// Clear reactions
				message.clearReactions().queue(
					success -> System.out.println("Reactions cleared successfully!"),
					error -> System.err.println("Failed to clear reactions: " + error.getMessage())
				);
			}
			else if (emoji.equals(RED_CROSS))
			{
				// Update the embed to have the red cross thumbnail
				updateMessageThumbnail(message, RED_CROSS_URL);

				// Update the status in the database
				updateQuestionStatus(repostedMessageId, "denied");

				// Optionally, notify the user about the denial here

				// Clear reactions
				message.clearReactions().queue(
					success -> System.out.println("Reactions cleared successfully!"),
					error -> System.err.println("Failed to clear reactions: " + error.getMessage())
				);
			}
		}, error -> System.err.println("Error retrieving message: " + error.getMessage()));
	}

	public void repostQuestionFromRepostedMessageId(String repostedMessageId, User contributorUser, TextChannel approvedChannel) {
		String sqlSelect = "SELECT id, message_content, author_name, author_avatar_url FROM questions WHERE reposted_message_id = ?";
		String sqlUpdate = "UPDATE questions SET status = 'approved' WHERE reposted_message_id = ?";

		try (Connection conn = DriverManager.getConnection("jdbc:sqlite:questions.db");
			 PreparedStatement pstmtSelect = conn.prepareStatement(sqlSelect);
			 PreparedStatement pstmtUpdate = conn.prepareStatement(sqlUpdate)) {

			pstmtSelect.setString(1, repostedMessageId);  // Use reposted_message_id to select the message
			ResultSet rs = pstmtSelect.executeQuery();

			if (rs.next()) {
				int questionNumber = rs.getInt("id");
				String messageContent = rs.getString("message_content");  // Get the message content
				String authorName = rs.getString("author_name");  // Get the author's name
				String authorAvatarUrl = rs.getString("author_avatar_url");  // Get the author's avatar URL

				// Rebuild the message for reposting
				EmbedBuilder embedBuilder = new EmbedBuilder();
				embedBuilder
					.setAuthor(authorName, null, authorAvatarUrl)  // Use the stored author name and avatar URL
					.setTitle("Answered Question #" + questionNumber)
					.setDescription(messageContent)  // Use the message content from the database
					.setTimestamp(Instant.now())
					.setFooter(String.format("Answered by %s", contributorUser.getName()), contributorUser.getEffectiveAvatarUrl())
					.setColor(Color.GREEN);

				// Repost the message to the approved channel
				if (approvedChannel != null) {
					approvedChannel.sendMessageEmbeds(embedBuilder.build()).queue();
					System.out.println("Reposted the message from the database using reposted_message_id");
				}

				// Update the status to 'approved' in the database
				pstmtUpdate.setString(1, repostedMessageId);  // Set the reposted_message_id for the update
				pstmtUpdate.executeUpdate();  // Execute the update query
				System.out.println("Updated the status to 'approved' in the database for reposted_message_id: " + repostedMessageId);
			}
		} catch (SQLException e) {
			System.err.println("Error retrieving or updating message content: " + e.getMessage());
		}
	}

	public void transferQuestionToDiscussion(@Nonnull MessageReceivedEvent event, Question question)
	{
		System.out.println("TransferQuestionToDiscussion method called");

		// Post the message in the discussion channel
		TextChannel discussionChannel = event.getGuild().getTextChannelById(DISCUSSION_CHANNEL_ID);
		if (discussionChannel != null)
		{
			MessageChannelUnion channel = event.getChannel();
			System.out.printf("[%s] [%s] %s: %s\n", event.getGuild().getName(), channel, question.getAuthorName(), question.getMessageContent());

			EmbedBuilder embedBuilder = new EmbedBuilder();
			embedBuilder
				.setAuthor(question.getAuthorName(), null, question.getAuthorAvatarUrl())
				.setTitle("Asked Question #" + question.getQuestionNumber())
				.setDescription(question.getMessageContent())
				.setColor(getRandomColour())
				.setFooter(String.format("::Deny %s to attach a denial message", question.getQuestionNumber()))
				.setTimestamp(Instant.now())
				.setThumbnail(QUESTION_MARK_URL);


			discussionChannel.sendMessageEmbeds(embedBuilder.build()).queue(repostedMessage -> {
				// Add reactions to the reposted message
				repostedMessage.addReaction(GREEN_TICK).queue();
				repostedMessage.addReaction(RED_CROSS).queue();

				String sqlUpdate = "UPDATE questions SET reposted_message_id = ? WHERE message_id = ?";

				try (Connection conn = DriverManager.getConnection("jdbc:sqlite:questions.db");
					 PreparedStatement pstmt = conn.prepareStatement(sqlUpdate))
				{
					pstmt.setString(1, repostedMessage.getId());
					pstmt.setString(2, question.getMessageId());
					pstmt.executeUpdate();
					System.out.println("Reposted message ID saved to database");
				}
				catch (SQLException e)
				{
					System.err.println("Error updating reposted message ID: " + e.getMessage());
				}
			});
		}
		else
		{
			System.err.println("Error: Discussion channel not found, check if the channel ID is correct");
		}
	}

	private Color getRandomColour()
	{
		return new Color(
			ThreadLocalRandom.current().nextInt(256),
			ThreadLocalRandom.current().nextInt(256),
			ThreadLocalRandom.current().nextInt(256)
		);
	}
}
