package xyz.srnyx.howdyholidays;

import com.freya02.botcommands.api.components.Components;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import org.bson.conversions.Bson;

import org.jetbrains.annotations.NotNull;

import org.jetbrains.annotations.Nullable;
import xyz.srnyx.howdyholidays.config.HowdyConfig;
import xyz.srnyx.howdyholidays.listeners.MessageListener;
import xyz.srnyx.howdyholidays.mongo.Profile;

import xyz.srnyx.lazylibrary.LazyEmbed;
import xyz.srnyx.lazylibrary.LazyEmoji;
import xyz.srnyx.lazylibrary.LazyLibrary;
import xyz.srnyx.lazylibrary.settings.LazySettings;

import xyz.srnyx.magicmongo.MagicCollection;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;


public class HowdyHolidays extends LazyLibrary {
    @NotNull public static final Random RANDOM = new Random();

    @NotNull public final HowdyConfig config = new HowdyConfig(this);
    @NotNull public final ScheduledExecutorService executor_service = Executors.newSingleThreadScheduledExecutor();
    public long lastHouses;

    public HowdyHolidays() {
        jda.addEventListener(new MessageListener(this));
        jda.getPresence().setActivity(Activity.customStatus("Made by srnyx.com ❤"));
        LOGGER.info("Howdy Holidays has finished starting!");
    }

    @Override @NotNull
    public Consumer<LazySettings> getSettings() {
        return newSettings -> newSettings
                .searchPaths("xyz.srnyx.howdyholidays.commands")
                .gatewayIntents(
                        GatewayIntent.GUILD_MESSAGES,
                        GatewayIntent.GUILD_MESSAGE_REACTIONS)
                .disabledCacheFlags(
                        CacheFlag.ACTIVITY,
                        CacheFlag.EMOJI,
                        CacheFlag.VOICE_STATE,
                        CacheFlag.STICKER,
                        CacheFlag.CLIENT_STATUS,
                        CacheFlag.ONLINE_STATUS,
                        CacheFlag.SCHEDULED_EVENTS)
                .mongoCollection("profiles", Profile.class)
                .embedDefault(LazyEmbed.Key.COLOR, 16737380)
                .embedDefault(LazyEmbed.Key.FOOTER_TEXT, "Howdy Holidays")
                .embedDefault(LazyEmbed.Key.FOOTER_ICON, "https://us-east-1.tixte.net/uploads/cdn.venox.network/howdyholidays.jpg");
    }

    @NotNull
    public Set<Long> getRecentChatters(@NotNull MessageChannel channel) {
        final Set<Long> users = new HashSet<>();
        final long oldest = System.currentTimeMillis() - config.activityCheck.messages.oldest;
        int messages = config.activityCheck.messages.maximum;
        for (final Message message : channel.getIterableHistory()) {
            if (messages == 0 || message.getTimeCreated().toInstant().toEpochMilli() < oldest) break;
            final User user = message.getAuthor();
            if (user.isBot()) continue;
            messages--;
            users.add(user.getIdLong());
        }
        return users;
    }

    public void sendHouses(@NotNull MessageUnion union, @Nullable Boolean highRisk) {
        if (highRisk == null) highRisk = RANDOM.nextInt(100) < config.highRiskChance;
        final long endTime = (System.currentTimeMillis() / 1000) + config.times.reactions;

        // Build embed
        final String title = ":cowboy: Howdy Holidays!" + (highRisk ? " | HIGH-RISK" : "");
        final LazyEmbed embed = new LazyEmbed()
                .setColor(highRisk ? Color.RED : Color.GREEN)
                .setTitle(title)
                .setDescription((highRisk ? LazyEmoji.WARNING + " **__HIGH-RISK!__ 2 houses have lots of coal, 1 has lots of presents!** " + LazyEmoji.WARNING : "") + "\nSelect the house below you want to choose!\nEach house will give you a random amount of presents :gift:\n*Ending <t:" + endTime + ":R>*")
                .addField(config.guild.houses.getOneEmoji(), "**?**\n*0 selectors*", true)
                .addField(config.guild.houses.getTwoEmoji(), "**?**\n*0 selectors*", true)
                .addField(config.guild.houses.getThreeEmoji(), "**?**\n*0 selectors*", true);

        // Send message
        final AtomicBoolean ended = new AtomicBoolean(false);
        final HowdyHouses houses = new HowdyHouses();
        union.sendMessage(embed.build(this), Components.stringSelectionMenu(menuEvent -> {
            if (ended.get()) {
                menuEvent.deferEdit().queue();
                return;
            }

            // Get values
            final long userId = menuEvent.getUser().getIdLong();
            final List<String> values = menuEvent.getValues();
            if (values.isEmpty()) {
                houses.winnersOne.remove(userId);
                houses.winnersTwo.remove(userId);
                houses.winnersThree.remove(userId);
                menuEvent.editMessageEmbeds(updateEmbed(embed, houses)).queue();
                return;
            }

            // Set selected value
            final String value = values.get(0);
            switch (value) {
                case "one" -> {
                    houses.winnersOne.add(userId);
                    houses.winnersTwo.remove(userId);
                    houses.winnersThree.remove(userId);
                }
                case "two" -> {
                    houses.winnersOne.remove(userId);
                    houses.winnersTwo.add(userId);
                    houses.winnersThree.remove(userId);
                }
                case "three" -> {
                    houses.winnersOne.remove(userId);
                    houses.winnersTwo.remove(userId);
                    houses.winnersThree.add(userId);
                }
                default -> throw new IllegalStateException("Unexpected value: " + value);
            }

            // Edit embed
            menuEvent.editMessageEmbeds(updateEmbed(embed, houses)).queue();
        })
                .setMaxValues(1)
                .addOptions(config.guild.houses.all)
                .setPlaceholder("Choose a house for presents!").build()).complete().getIdLong();

        // Get reward amounts
        final List<Integer> rewards = new ArrayList<>();
        if (highRisk) {
            // -30 to -20, -30 to -20, 30 to 40
            rewards.add(RANDOM.nextInt(11) - 30);
            rewards.add(RANDOM.nextInt(11) - 30);
            rewards.add(RANDOM.nextInt(11) + 30);
        } else {
            // -5 to 5, 5 to 10, 10 to 15
            rewards.add(RANDOM.nextInt(11) - 5);
            rewards.add(RANDOM.nextInt(6) + 5);
            rewards.add(RANDOM.nextInt(6) + 10);
        }
        final int rewardOne = rewards.remove(RANDOM.nextInt(3));
        final int rewardTwo = rewards.remove(RANDOM.nextInt(2));
        final int rewardThree = rewards.get(0);

        executor_service.schedule(() -> {
            ended.set(true);

            // Get message
            final Message message = union.getMessage();
            if (message == null) {
                union.editMessage(LazyEmoji.WARNING + " **An unexpected error occurred!** Please notify <@242385234992037888> with this code: `HowdyHolidays-1`").queue();
                return;
            }

            // Modify embed
            final String rewardOneEmoji = rewardOne >= 0 ? ":gift:" : config.guild.coal;
            final String rewardTwoEmoji = rewardTwo >= 0 ? ":gift:" : config.guild.coal;
            final String rewardThreeEmoji = rewardThree >= 0 ? ":gift:" : config.guild.coal;
            embed
                    .setTitle(title + " | ENDED")
                    .setDescription("The houses have disappeared!\nPresents are being handed out now... *coal removes presents D:*\n*Ended <t:" + endTime + ":R>*")
                    .clearFields()
                    .addField(config.guild.houses.getOneEmoji(), "**" + Math.abs(rewardOne) + "** " + rewardOneEmoji + "\n*" + houses.winnersOne.size() + " selectors*", true)
                    .addField(config.guild.houses.getTwoEmoji(), "**" + Math.abs(rewardTwo) + "** " + rewardTwoEmoji + "\n*" + houses.winnersTwo.size() + " selectors*", true)
                    .addField(config.guild.houses.getThreeEmoji(), "**" + Math.abs(rewardThree) + "** " + rewardThreeEmoji + "\n*" + houses.winnersThree.size() + " selectors*", true);

            // Edit message
            union.editEmbed(embed.build(this)).queue();

            // Give present
            final MagicCollection<Profile> collection = mongo.getCollection(Profile.class);
            final Bson updateOne = Updates.inc("presents", rewardOne);
            final Bson updateTwo = Updates.inc("presents", rewardTwo);
            final Bson updateThree = Updates.inc("presents", rewardThree);
            final long self = jda.getSelfUser().getIdLong();
            houses.winnersOne.forEach(user -> {
                if (user != self) collection.upsertOne(Filters.eq("user", user), updateOne);
            });
            houses.winnersTwo.forEach(user -> {
                if (user != self) collection.upsertOne(Filters.eq("user", user), updateTwo);
            });
            houses.winnersThree.forEach(user -> {
                if (user != self) collection.upsertOne(Filters.eq("user", user), updateThree);
            });
        }, config.times.reactions, TimeUnit.SECONDS);
    }

    @NotNull
    private MessageEmbed updateEmbed(@NotNull LazyEmbed embed, @NotNull HowdyHouses houses) {
        final HowdyConfig.GuildNode.HousesNode housesNode = config.guild.houses;
        return embed
                .clearFields()
                .addField(housesNode.getOneEmoji(), "**?**\n*" + houses.winnersOne.size() + " selectors*", true)
                .addField(housesNode.getTwoEmoji(), "**?**\n*" + houses.winnersTwo.size() + " selectors*", true)
                .addField(housesNode.getThreeEmoji(), "**?**\n*" + houses.winnersThree.size() + " selectors*", true)
                .build(this);
    }

    public static void main(@NotNull String[] arguments) {
        new HowdyHolidays();
    }
}
