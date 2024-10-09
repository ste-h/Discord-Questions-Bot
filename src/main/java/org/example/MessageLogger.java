package org.example;

import java.awt.Color;
import java.time.Instant;
import java.util.EnumSet;
import javax.annotation.Nonnull;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Message;
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
	// See https://emojipedia.org/red-heart/ and find the codepoints
	public static final Emoji GREEN_TICK = Emoji.fromUnicode("U+2705");
	public static final Emoji RED_CROSS = Emoji.fromUnicode("U+274C");
	public static final String SUBMISSION_CHANNEL_ID = "1291263144701722696";
	public static final String DISCUSSION_CHANNEL_ID = "1292697957422071881";

	Message askedQuestion;

	public static void main(String[] args)
	{
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

	@Override
	public void onMessageReceived(@Nonnull MessageReceivedEvent event)
	{
		String channelId = event.getGuildChannel().getId();

		// Question posted in submission channel
		if (event.isFromGuild() && channelId.equals(SUBMISSION_CHANNEL_ID))
		{
			transferQuestionToDiscussion(event);
		}
	}

	public void onMessageReactionAdd(@Nonnull MessageReactionAddEvent event)
	{
		if (event.getUser().isBot())
		{
			return;
		}

		event.retrieveMessage().queue(message -> {
			askedQuestion = message;
			System.out.println("Asked question: " + askedQuestion.getAuthor());

			Emoji emoji = event.getReaction().getEmoji();
			User contributorUser = event.getUser();

			if (emoji.equals(GREEN_TICK))
			{
				EmbedBuilder embedBuilder = new EmbedBuilder(message.getEmbeds().get(0));
				embedBuilder.setTimestamp(Instant.now());
				embedBuilder.setFooter(
					String.format("Message Answered by %s", contributorUser.getName()),
					contributorUser.getEffectiveAvatarUrl()
				);
				message.editMessageEmbeds(embedBuilder.build()).queue();

				System.out.println("Tick reaction added!");
			}
			else if (emoji.equals(RED_CROSS))
			{
				// Modify to be denied and message the user
				System.out.println("Cross reaction added!");
			}
		}, error -> System.err.println("Error retrieving message: " + error.getMessage()));
	}


	public void transferQuestionToDiscussion(@Nonnull MessageReceivedEvent event)
	{
		System.out.println("TransferQuestionToDiscussion method called");

		User author = event.getAuthor();
		MessageChannelUnion channel = event.getChannel();
		Message message = event.getMessage();

		System.out.printf("[%s] [%#s] %#s: %s\n", event.getGuild().getName(), channel, author, message.getContentDisplay());

		// Delete the message
//		message.delete().queue(null, error -> System.err.println("Error deleting message: " + error.getMessage()));

		// Post the message in the discussion channel
		TextChannel discussionChannel = event.getGuild().getTextChannelById(DISCUSSION_CHANNEL_ID);
		if (discussionChannel != null)
		{

			EmbedBuilder embedBuilder = new EmbedBuilder();

			embedBuilder.setTitle("Embedded Message Title").setDescription("This is an embedded message.").setColor(Color.BLUE).addField("Field Title", "Field content", false).setFooter("This is the footer text");

			discussionChannel.sendMessageEmbeds(embedBuilder.build()).queue(msg -> {
				msg.addReaction(GREEN_TICK).queue();
				msg.addReaction(RED_CROSS).queue();
			});
		}
		else
		{
			System.err.println("Error: Discussion channel not found, check if the channel ID is correct");

		}
	}
}

