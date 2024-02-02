package xyz.srnyx.howdyholidays.listeners;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import org.jetbrains.annotations.NotNull;

import xyz.srnyx.lazylibrary.LazyListener;

import xyz.srnyx.howdyholidays.MessageUnion;
import xyz.srnyx.howdyholidays.HowdyHolidays;


public class MessageListener extends LazyListener {
    @NotNull private final HowdyHolidays howdyHolidays;
    private long lastCheck;

    public MessageListener(@NotNull HowdyHolidays howdyHolidays) {
        this.howdyHolidays = howdyHolidays;
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!(event.getChannel() instanceof TextChannel channel) || event.getAuthor().isBot() || channel.getIdLong() != howdyHolidays.config.guild.channels.general.id()) return;
        final long now = System.currentTimeMillis();

        // Check last houses & check
        if (now - howdyHolidays.lastHouses < howdyHolidays.config.times.housesCooldown || now - lastCheck < howdyHolidays.config.activityCheck.cooldown) return;
        lastCheck = now;

        // Send if there is activity
        if (howdyHolidays.getRecentChatters(channel).size() >= howdyHolidays.config.activityCheck.requiredUsers) {
            howdyHolidays.lastHouses = now;
            howdyHolidays.sendHouses(new MessageUnion(channel), null);
        }
    }
}
