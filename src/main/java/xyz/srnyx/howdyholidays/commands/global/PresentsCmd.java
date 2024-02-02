package xyz.srnyx.howdyholidays.commands.global;

import com.freya02.botcommands.api.annotations.CommandMarker;
import com.freya02.botcommands.api.annotations.Dependency;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.CommandScope;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GlobalSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import net.dv8tion.jda.api.entities.User;

import org.bson.conversions.Bson;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import xyz.srnyx.lazylibrary.LazyEmbed;
import xyz.srnyx.lazylibrary.LazyEmoji;

import xyz.srnyx.howdyholidays.HowdyHolidays;
import xyz.srnyx.howdyholidays.mongo.Profile;

import xyz.srnyx.magicmongo.MagicCollection;

import java.awt.*;
import java.util.Set;


@CommandMarker
public class PresentsCmd extends ApplicationCommand {
    @Dependency private HowdyHolidays howdyHolidays;

    @JDASlashCommand(
            scope = CommandScope.GLOBAL,
            name = "presents",
            subcommand = "get",
            description = "How many presents do you got?!")
    public void commandGet(@NotNull GlobalSlashEvent event,
                           @AppOption(description = "The user to get the presents of") @Nullable User user) {
        final long eventUserId = event.getUser().getIdLong();
        final long userId = user != null ? user.getIdLong() : eventUserId;
        boolean otherUser = eventUserId != userId;
        final Profile profile = howdyHolidays.mongo.getCollection(Profile.class).findOne("user", userId);
        final int presents = profile == null ? 0 : profile.getPresents();
        event.replyEmbeds(new LazyEmbed()
                .setTitle("Presents Amount")
                .setDescription((otherUser ? user.getAsMention() + " has" : "You have") + " **" + presents + "** :gift:").build(howdyHolidays)).queue();
    }

    @JDASlashCommand(
            scope = CommandScope.GLOBAL,
            name = "presents",
            subcommand = "daily",
            description = "Claim your daily presents!")
    public void commandDaily(@NotNull GlobalSlashEvent event) {
        final MagicCollection<Profile> collection = howdyHolidays.mongo.getCollection(Profile.class);
        final long userId = event.getUser().getIdLong();
        final Bson filter = Filters.eq("user", userId);

        // Get profile
        Profile profile = collection.findOne(filter);
        if (profile == null) {
            profile = new Profile(userId);
            collection.collection.insertOne(profile);
        }

        // Check if they can claim
        final Long nextDaily = profile.lastDaily == null ? null : profile.lastDaily + 86400000;
        if (nextDaily != null && System.currentTimeMillis() < nextDaily) {
            event.reply(LazyEmoji.NO + " You've already claimed your presents today! You can claim again **<t:" + (nextDaily / 1000) + ":R>**!").setEphemeral(true).queue();
            return;
        }

        // Get amount
        final boolean isVip = howdyHolidays.config.guild.roles.isVip(userId);
        int amount = HowdyHolidays.RANDOM.nextInt(11) + 15;
        if (isVip) amount = (int) Math.round(amount * howdyHolidays.config.vipDailyMultiplier);

        // Update document
        collection.updateOne(filter, Updates.combine(Updates.inc("presents", amount), Updates.set("lastDaily", System.currentTimeMillis())));

        // Send embed
        final LazyEmbed embed = new LazyEmbed()
                .setTitle("Daily Presents")
                .setDescription("You've successfully claimed your daily presents!");
        if (isVip) embed
                .setColor(Color.MAGENTA)
                .addField("VIP Multiplier", "x" + howdyHolidays.config.vipDailyMultiplier + " :zap:", true);
        event.replyEmbeds(embed
                .addField("Amount claimed", "+" + amount + " :gift:", true)
                .addField("New presents", (profile.getPresents() + amount) + " :gift:", true)
                .build(howdyHolidays)).queue();
    }

    @JDASlashCommand(
            scope = CommandScope.GLOBAL,
            name = "presents",
            subcommand = "give",
            description = "MANAGER-ONLY | Give presents to a user")
    public void commandGive(@NotNull GlobalSlashEvent event,
                            @AppOption(description = "The user to give the presents to") @NotNull User user,
                            @AppOption(description = "The amount of presents to give") int amount) {
        if (howdyHolidays.config.guild.roles.manager.checkDontHaveRole(event)) return;

        // Get new profile
        final Profile profile = howdyHolidays.mongo.getCollection(Profile.class).findOneAndUpsert(Filters.eq("user", user.getIdLong()), Updates.inc("presents", amount));
        if (profile == null) {
            event.reply(LazyEmoji.NO + " An unexpected error occurred!").setEphemeral(true).queue();
            return;
        }

        // Reply
        event.replyEmbeds(new LazyEmbed()
                .setTitle("Give Presents")
                .setDescription("You gave **" + amount + "** :gift: to " + user.getAsMention())
                .addField("New amount", profile.getPresents() + " :gift:", true).build(howdyHolidays)).queue();
    }

    @JDASlashCommand(
            scope = CommandScope.GLOBAL,
            name = "presents",
            subcommand = "take",
            description = "MANAGER-ONLY | Take presents from a user")
    public void commandTake(@NotNull GlobalSlashEvent event,
                            @AppOption(description = "The user to take the presents from") @NotNull User user,
                            @AppOption(description = "The amount of presents to take") int amount) {
        if (howdyHolidays.config.guild.roles.manager.checkDontHaveRole(event)) return;

        // Get new profile
        final Profile profile = howdyHolidays.mongo.getCollection(Profile.class).findOneAndUpsert(Filters.eq("user", user.getIdLong()), Updates.inc("presents", -amount));
        if (profile == null) {
            event.reply(LazyEmoji.NO + " An unexpected error occurred!").setEphemeral(true).queue();
            return;
        }

        // Reply
        event.replyEmbeds(new LazyEmbed()
                .setTitle("Take Presents")
                .setDescription("You took **" + amount + "** :gift: from " + user.getAsMention())
                .addField("New amount", profile.getPresents() + " :gift:", true).build(howdyHolidays)).queue();
    }

    @JDASlashCommand(
            scope = CommandScope.GLOBAL,
            name = "presents",
            subcommand = "set",
            description = "MANAGER-ONLY | Set the amount of presents of a user")
    public void commandSet(@NotNull GlobalSlashEvent event,
                           @AppOption(description = "The user to set the presents of") @NotNull User user,
                           @AppOption(description = "The amount of presents to set") int amount) {
        if (howdyHolidays.config.guild.roles.manager.checkDontHaveRole(event)) return;

        // Get new profile
        final Profile profile = howdyHolidays.mongo.getCollection(Profile.class).findOneAndUpsert(Filters.eq("user", user.getIdLong()), Updates.set("presents", amount));
        if (profile == null) {
            event.reply(LazyEmoji.NO + " An unexpected error occurred!").setEphemeral(true).queue();
            return;
        }

        // Reply
        event.replyEmbeds(new LazyEmbed()
                .setTitle("Set Presents")
                .setDescription("You set " + user.getAsMention() + "'s presents to **" + amount + "** :gift:").build(howdyHolidays)).queue();
    }

    @JDASlashCommand(
            scope = CommandScope.GLOBAL,
            name = "presents",
            subcommand = "party",
            description = "MANAGER-ONLY | Give all recent chatters some presents")
    public void commandAll(@NotNull GlobalSlashEvent event,
                           @AppOption(description = "The amount of presents to give") int amount) {
        if (howdyHolidays.config.guild.roles.manager.checkDontHaveRole(event)) return;
        final long userId = event.getUser().getIdLong();

        // Get recent chatters
        final Set<Long> recentChatters = howdyHolidays.getRecentChatters(event.getChannel());
        recentChatters.remove(userId);
        if (recentChatters.isEmpty()) {
            event.reply(LazyEmoji.NO + " No recent chatters!").setEphemeral(true).queue();
            return;
        }

        // Give presents
        final MagicCollection<Profile> collection = howdyHolidays.mongo.getCollection(Profile.class);
        final Bson update = Updates.inc("presents", amount);
        for (final long id : recentChatters) collection.upsertOne(Filters.eq("user", id), update);

        // Reply
        final StringBuilder mentions = new StringBuilder();
        for (final long id : recentChatters) mentions.append("<@").append(id).append(">, ");
        mentions.setLength(mentions.length() - 2);
        event.replyEmbeds(new LazyEmbed()
                .setTitle(":partying_face: Present Party!")
                .setDescription("<@" + userId + "> gifted **" + amount + "** :gift: to all recent chatters! :D\n\n**Recent chatters:** " + mentions)
                .build(howdyHolidays)).queue();
    }
}
