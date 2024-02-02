package xyz.srnyx.howdyholidays.config;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import xyz.srnyx.howdyholidays.HowdyHolidays;

import xyz.srnyx.lazylibrary.LazyEmbed;


public record HowdyRole(@NotNull HowdyHolidays howdyHolidays, long id) {
    @Nullable
    public Role getRole() {
        final Guild guild = howdyHolidays.config.guild.getGuild();
        return guild == null ? null : guild.getRoleById(id);
    }

    public boolean hasRole(@NotNull Member member) {
        final Role jdaRole = getRole();
        return jdaRole != null && member.getRoles().contains(jdaRole);
    }

    public boolean hasRole(long userId) {
        final Guild guild = howdyHolidays.config.guild.getGuild();
        return guild != null && hasRole(guild.retrieveMemberById(userId).complete());
    }

    public boolean checkDontHaveRole(@NotNull GenericCommandInteractionEvent event) {
        final boolean doesntHaveRole = !hasRole(event.getUser().getIdLong());
        if (doesntHaveRole) event.replyEmbeds(LazyEmbed.noPermission("<@&" + id + ">").build(howdyHolidays)).setEphemeral(true).queue();
        return doesntHaveRole;
    }
}
