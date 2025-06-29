# VillagerGPT

VillagerGPT breathes new life into your server's villagers, allowing players to talk and trade with them using the same technology that powers ChatGPT. Players can have a conversation with any villager and haggle for better deals, or come up with previously impossible trades.

AI villagers are aware of various aspects of the game world, their reputation with players, and they each have their own distinct personality based on their profession and a randomly-chosen personality archetype.

## Requirements

VillagerGPT requires **Java 21** or newer to build and run. Make sure a compatible JDK is installed and selected when compiling the plugin.

## Example Conversations

![](https://cdn.modrinth.com/data/HQ2FTKZf/images/57fca3de995cff548867bc49aa507e554c71d93d.png)

![](https://cdn.modrinth.com/data/HQ2FTKZf/images/2c68579ab81cf4ef25c63bb5b2b11373dba69cda.jpeg)

## Usage

To start a conversation with a villager, use the command `/ttv` ("talk to villager") then right-click on the villager you'd like to speak to. Once the conversation has started, simply send chat messages to continue. You can end the conversation with `/ttvend`, or by walking away from the villager.

If an AI villager proposes a trade, you can act on it by opening the trading menu like normal (right-clicking on the villager).

## AI Villager Capabilities

AI villagers have access to the following information:

- World information
  - Biome they are in
  - Time of day
  - Weather
  - Nearby events such as changes in time, weather or approaching mobs
- Player information
  - Username
  - [Reputation score](https://minecraft.fandom.com/wiki/Villager#Gossiping)
- Villager information
  - Name (including custom names)
  - Profession
  - Short summary of past conversations

They can perform the following actions in their responses:

- Propose trades
- Shake head
- Play various sounds
- Recall and share gossip with nearby villagers

Villagers will also react to their surroundings. The plugin watches the
environment during a conversation and inserts messages when the weather
changes, night falls or new mobs come close.

AI villagers also have one of these randomly selected personalities:

- Elder: "As an elder of the village, you have seen and done many things across the years"
- Optimist: "You are an optimist that always tries to look on the bright side of things"
- Grumpy: "You are a grump that isn't afraid to speak his mind"
- Barterer: "You are a shrewd trader that has much experience in bartering"
- Jester: "You enjoy telling funny jokes and are generally playful toward players"
- Serious: "You are serious and to-the-point; you do not have much patience for small talk"
- Empath: "You are a kind person and very empathetic to others' situations"

## Configuration

The plugin can operate in two modes controlled by the `provider` option in `config.yml`.
When set to `openai`, an OpenAI API key is required. Obtain one [here](https://platform.openai.com/) and place it under `openai-key` in the config.
If `provider` is set to `local`, VillagerGPT will POST the current conversation to the URL defined by `local-model-url` and use the response as the villager's reply.

Set `villagers-aware-during-conversation` to `true` if you want villagers to keep moving while talking.

### GPT-4

If you have GPT-4 access, it is highly recommended you switch the model in the config to use GPT-4 instead of the default model. GPT-4 is significantly better at listening to the `system` message and thus following instructions.

You can switch to GPT-4 by replacing `openai-model` in `config.yml` with `gpt-4`.


### Conversation Memory

VillagerGPT keeps a history of each villager's conversations in a small SQLite
database. The location of this database and how many messages are stored can be
changed in `config.yml` under the `memory` section.

The conversation history limit for each villager is configured with the
`memory.max-messages` option.

Each villager is also assigned a random name the first time you speak to them.
VillagerGPT keeps a short summary of recent interactions which is provided to
the AI at the start of future conversations.

### Gossip

Villagers remember interesting pieces of gossip. When two villagers stand near
each other they will exchange a few of these rumors. Gossip is loaded into the
start of any conversation, giving you insight into what villagers have been
talking about. Control the sharing radius and how many entries are kept with the
`gossip` section in `config.yml`.

### Environment

VillagerGPT watches the surroundings during conversations. Adjust how far away
entities are detected using `environment.radius` and how often checks occur with
`environment.interval` in `config.yml`.

### Local Model

Set `provider` to `local` to use a locally hosted language model. Configure the
endpoint with `local-model-url`. By default the conversation is sent as plain
text and the response body is used as the villager's reply. Set
`local-model-json` to `true` if your model expects a JSON payload containing the
conversation messages.



## Commands

- `/ttv`: Initiate a conversation with a villager
- `/ttvclear`: Clear the current villager conversation
- `/ttvend`: End the current villager conversation

## Permissions

The following permissions are available:

- `villagergpt.ttv`: Allow use of the `/ttv` command
- `villagergpt.ttvclear`: Allow use of the `/ttvclear` command
- `villagergpt.ttvend`: Allow use of the `/ttvend` command
