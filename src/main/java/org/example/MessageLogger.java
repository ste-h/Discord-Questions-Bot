package org.example;

import io.github.cdimascio.dotenv.Dotenv;
import java.awt.Color;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
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
	static Dotenv dotenv = Dotenv.load();

	private final DatabaseManager dbManager = DatabaseManager.getInstance();

	public static final Emoji GREEN_TICK = Emoji.fromUnicode("U+2705");
	public static final Emoji RED_CROSS = Emoji.fromUnicode("U+274C");
	public static final Emoji QUESTION_MARK = Emoji.fromUnicode("U+2753");
	public static final String SUBMISSION_CHANNEL_ID = dotenv.get("SUBMISSION_CHANNEL_ID");
	public static final String DISCUSSION_CHANNEL_ID = dotenv.get("DISCUSSION_CHANNEL_ID");
	public static final String APPROVED_CHANNEL_ID = dotenv.get("APPROVED_CHANNEL_ID");
	public static final String QUESTION_MARK_URL = dotenv.get("QUESTION_MARK_URL");
	public static final String RED_CROSS_URL = dotenv.get("RED_CROSS_URL");
	public static final String GREEN_TICK_URL = dotenv.get("GREEN_TICK_URL");

	Message askedQuestion;

	public static void main(String[] args) {
		// DatabaseManager will handle table creation
		DatabaseManager.getInstance();

		String token = System.getenv("TOKEN");
		if (token == null || token.isEmpty()) {
			System.err.println("Error: The TOKEN environment variable is not set.");
			System.exit(1);
		}

		EnumSet<GatewayIntent> intents = EnumSet.of(
			GatewayIntent.GUILD_MESSAGES,
			GatewayIntent.DIRECT_MESSAGES,
			GatewayIntent.MESSAGE_CONTENT,
			GatewayIntent.GUILD_MESSAGE_REACTIONS,
			GatewayIntent.DIRECT_MESSAGE_REACTIONS
		);

		try {
			JDA jda = JDABuilder.createLight(token, intents)
				.addEventListeners(new MessageLogger())
				.setActivity(Activity.watching("your messages"))
				.build();

			jda.getRestPing().queue(ping ->
				System.out.println("Logged in with ping: " + ping)
			);

			jda.awaitReady();

			System.out.println("Guilds: " + jda.getGuildCache().size());
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onMessageReceived(@Nonnull MessageReceivedEvent event) {
		// Ignore bot messages
		if (event.getAuthor().isBot()) {
			return;
		}

		String messageContentRaw = event.getMessage().getContentRaw();

		if (messageContentRaw.startsWith("::deny ")) {
			handleDenyCommand(event, messageContentRaw);
		}

		if (messageContentRaw.startsWith("::unans")) {
			handleUnansCommand(event);
		}

		// Get the channel ID where the message is coming from
		String channelId = event.getGuildChannel().getId();

		// Only process if it's coming from the designated submission channel
		if (event.isFromGuild() && channelId.equals(SUBMISSION_CHANNEL_ID)) {
			String messageId = event.getMessage().getId();
			String authorId = event.getAuthor().getId();
			String authorName = event.getAuthor().getName();
			String authorAvatarUrl = event.getAuthor().getEffectiveAvatarUrl();
			String messageContent = event.getMessage().getContentRaw();
			String status = "submitted";

			// Create a new Question object
			Question question = new Question(
				messageId, authorId, authorName, authorAvatarUrl, messageContent, status
			);

			// Insert the question into the database
			int questionNumber = DatabaseManager.getInstance().insertQuestion(question);

			if (questionNumber != -1) {
				// If insertion is successful, update the question number and proceed
				question.setQuestionNumber(questionNumber);
				System.out.println("Message inserted into database with question number: " + questionNumber);

				// Transfer question to the discussion channel (if applicable)
				transferQuestionToDiscussion(event, question);
			} else {
				// Log the failure for debugging
				System.err.println("Failed to insert question into the database.");
			}

			// Delete the original message from the submission channel
			event.getMessage().delete().queue();
		}
	}

	@Override
	public void onMessageReactionAdd(@Nonnull MessageReactionAddEvent event) {
		if (event.getUser().isBot()) {
			return;
		}

		Emoji emoji = event.getReaction().getEmoji();
		User contributorUser = event.getUser();

		// Retrieve the message that was reacted to
		event.retrieveMessage().queue(message -> {
			String repostedMessageId = message.getId();

			// Retrieve the question from the database using the reposted message ID
			Question question = dbManager.getQuestionByRepostedMessageId(repostedMessageId);

			if (question != null) {
				TextChannel approvedChannel = event.getGuild().getTextChannelById(APPROVED_CHANNEL_ID);

				if (emoji.equals(GREEN_TICK)) {
					// Update the question's status to 'approved'
					question.setStatus("approved");
					dbManager.updateQuestionStatus(question.getMessageId(), "approved");

					// Repost the question to the approved channel
					if (approvedChannel != null) {
						repostQuestionToApprovedChannel(question, contributorUser, approvedChannel);
					} else {
						System.err.println("Approved channel not found.");
					}

					// Update the embed to have the green tick thumbnail
					updateMessageThumbnail(message, GREEN_TICK_URL);

					// Clear reactions
					message.clearReactions().queue(
						success -> System.out.println("Reactions cleared successfully!"),
						error -> System.err.println("Failed to clear reactions: " + error.getMessage())
					);
				} else if (emoji.equals(RED_CROSS)) {
					// Update the question's status to 'denied'
					question.setStatus("denied");
					dbManager.updateQuestionStatus(question.getMessageId(), "denied");

					// Update the embed to have the red cross thumbnail
					updateMessageThumbnail(message, RED_CROSS_URL);

					// Optionally, notify the user about the denial here

					// Clear reactions
					message.clearReactions().queue(
						success -> System.out.println("Reactions cleared successfully!"),
						error -> System.err.println("Failed to clear reactions: " + error.getMessage())
					);
				}
			} else {
				System.err.println("Question not found for repostedMessageId: " + repostedMessageId);
			}
		}, error -> System.err.println("Error retrieving message: " + error.getMessage()));
	}

	private void transferQuestionToDiscussion(@Nonnull MessageReceivedEvent event, Question question) {
		System.out.println("TransferQuestionToDiscussion method called");

		// Post the message in the discussion channel
		TextChannel discussionChannel = event.getGuild().getTextChannelById(DISCUSSION_CHANNEL_ID);
		if (discussionChannel != null) {
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

				// Update the question's repostedMessageId
				question.setRepostedMessageId(repostedMessage.getId());
				dbManager.updateRepostedMessageId(question.getMessageId(), question.getRepostedMessageId());

				System.out.println("Reposted message ID saved to database");
			});
		} else {
			System.err.println("Error: Discussion channel not found, check if the channel ID is correct");
		}
	}

	private void repostQuestionToApprovedChannel(Question question, User contributorUser, TextChannel approvedChannel) {
		EmbedBuilder embedBuilder = new EmbedBuilder();
		embedBuilder
			.setAuthor(question.getAuthorName(), null, question.getAuthorAvatarUrl())
			.setTitle("Answered Question #" + question.getId())
			.setDescription(question.getMessageContent())
			.setTimestamp(Instant.now())
			.setFooter(String.format("Answered by %s", contributorUser.getName()), contributorUser.getEffectiveAvatarUrl())
			.setColor(Color.GREEN);

		// Repost the message to the approved channel
		approvedChannel.sendMessageEmbeds(embedBuilder.build()).queue(
			success -> System.out.println("Question reposted to approved channel."),
			error -> System.err.println("Failed to repost question: " + error.getMessage())
		);
	}

	private void updateMessageThumbnail(Message message, String thumbnailUrl) {
		if (!message.getEmbeds().isEmpty()) {
			MessageEmbed oldEmbed = message.getEmbeds().get(0);
			EmbedBuilder embedBuilder = new EmbedBuilder(oldEmbed);

			embedBuilder.setThumbnail(thumbnailUrl);

			message.editMessageEmbeds(embedBuilder.build()).queue(
				success -> System.out.println("Thumbnail updated successfully!"),
				error -> System.err.println("Failed to update thumbnail: " + error.getMessage())
			);
		} else {
			System.err.println("No embed found in the message to edit.");
		}
	}

	private void handleDenyCommand(MessageReceivedEvent event, String messageContent) {
		try {
			// Parse the command: ::deny messagenumber message
			String[] parts = messageContent.split(" ", 3);
			if (parts.length < 3) {
				event.getChannel().sendMessage("Invalid command format. Use: ::deny messagenumber message").queue();
				return;
			}

			int questionNumber = Integer.parseInt(parts[1]);
			String denyMessage = parts[2];

			// Retrieve the question by question number
			Question question = dbManager.getQuestionByNumber(questionNumber);

			if (question == null) {
				event.getChannel().sendMessage("No question found with number: " + questionNumber).queue();
				return;
			}

			// Retrieve the user who submitted the question
			String authorId = question.getAuthorId();
			User user = event.getJDA().retrieveUserById(authorId).complete();

			if (user != null) {
				// Send a denial DM to the user
				user.openPrivateChannel().queue(privateChannel -> {
					EmbedBuilder embedBuilder = new EmbedBuilder()
						.setTitle("Your Question was Denied")
						.setDescription("**Reason:** " + denyMessage)
						.setColor(Color.RED)
						.setFooter("Question ID: " + questionNumber, null);

					privateChannel.sendMessageEmbeds(embedBuilder.build()).queue(
						success -> event.getChannel().sendMessage("Denial message sent to the user.").queue(),
						error -> event.getChannel().sendMessage("Failed to send denial message: " + error.getMessage()).queue()
					);
				});

				// Update the question's status in the database
				dbManager.updateQuestionStatus(question.getMessageId(), "denied");
			} else {
				event.getChannel().sendMessage("User not found or cannot receive messages.").queue();
			}
		} catch (NumberFormatException e) {
			event.getChannel().sendMessage("Invalid question number. Use: ::deny messagenumber message").queue();
		} catch (Exception e) {
			event.getChannel().sendMessage("An error occurred while processing the command: " + e.getMessage()).queue();
		}
	}

	private void handleUnansCommand(MessageReceivedEvent event) {
		try {
			List<Question> unansweredQuestions = dbManager.getQuestionsByStatus("submitted", 50);
			int unansweredCount = unansweredQuestions.size();

			if (unansweredCount == 0) {
				event.getChannel().sendMessage("There are no unanswered questions at the moment.").queue();
				return;
			}

			// Create the embed
			EmbedBuilder embedBuilder = new EmbedBuilder();
			embedBuilder.setTitle(unansweredCount + " Unanswered Questions in the Last 50 Questions");
			embedBuilder.setColor(getRandomColour());

			// Build a single string with questions separated by commas
			StringBuilder questionsList = new StringBuilder();
			for (Question question : unansweredQuestions) {
				// Generate the hyperlink for each question
				String questionUrl = String.format(
					"https://discord.com/channels/%s/%s/%s",
					event.getGuild().getId(), DISCUSSION_CHANNEL_ID, question.getRepostedMessageId()
				);

				// Append the question with a comma
				questionsList.append(String.format("[#%d](%s), ", question.getId(), questionUrl));
			}

			// Remove the trailing comma and space
			if (questionsList.length() > 0) {
				questionsList.setLength(questionsList.length() - 2);
			}

			// Add the questions as a single field
			embedBuilder.addField("", questionsList.toString(), false);
			embedBuilder.setThumbnail(QUESTION_MARK_URL);

			// Send the embed
			event.getChannel().sendMessageEmbeds(embedBuilder.build()).queue();
		} catch (Exception e) {
			event.getChannel().sendMessage("An error occurred while fetching unanswered questions: " + e.getMessage()).queue();
		}
	}


	private Color getRandomColour() {
		Random random = new Random();
		return new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));
	}
}
