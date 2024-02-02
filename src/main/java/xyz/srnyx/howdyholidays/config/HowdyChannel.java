package xyz.srnyx.howdyholidays.config;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import xyz.srnyx.howdyholidays.HowdyHolidays;


public record HowdyChannel(@NotNull HowdyHolidays howdyHolidays, long id) {
    @Nullable
    public GuildMessageChannel getChannel() {
        final Guild guild = howdyHolidays.config.guild.getGuild();
        return guild == null ? null : guild.getChannelById(GuildMessageChannel.class, id);
    }
}
