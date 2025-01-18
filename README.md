# Overview
This bot was created for a fairly specialized use-case for a couple of Discord servers ran by friends. Users are able to post questions, and then have them answered at a later date in a more structured way and without discussion pushing the questions out of the chat. 

Some examples of the types of servers where this bot might be useful is:
- Read-only Discord servers, servers that don't have a discussion channel but a place where users can ask questions and get answers is helpful
- Servers where there's a high amount of questions and having them all in the same channel to allow users to search through them is helpful, ie. a server for a unit at university, where similar questions may be asked multiple times, and a having more detailed responses in an easy to search channel is helpful

## Setup and Installation

### Prerequisites:
- Java 17 or higher
- SQLite
- Discord bot token
- .env file for configuration

Environment Variables
Create a .env file in the project root with the following keys:
```
TOKEN=your_discord_bot_token
SUBMISSION_CHANNEL_ID=channel_id_for_submissions
DISCUSSION_CHANNEL_ID=channel_id_for_discussions
APPROVED_CHANNEL_ID=channel_id_for_approvals
QUESTION_MARK_URL=url_to_question_mark_icon
RED_CROSS_URL=url_to_red_cross_icon
GREEN_TICK_URL=url_to_green_tick_icon
```
### Build and Run

Use Gradle to build the project.

Run the MessageLogger class to start the bot.


### Usage
Create 3 channels Discord channels:
- A submission channel
- A discussion channel, this is where the message will be discussed/approved, so limit Discord visiblity permissions to roles that should do question reviewing
- A approved channel, this is where approved questions will be moved to, post the answer here after approving

You can deny a question alongside a denail reason by using the ::deny <questionNumber> <reason> command

Use the ::unans command in the discussion channel to get a list of the last 50 unanswered questions
