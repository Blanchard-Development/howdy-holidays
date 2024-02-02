package xyz.srnyx.howdyholidays.commands.global;

import com.freya02.botcommands.api.annotations.CommandMarker;
import com.freya02.botcommands.api.annotations.Dependency;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.CommandScope;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GlobalSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.components.InteractionConstraints;

import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Sorts;

import net.dv8tion.jda.api.entities.MessageEmbed;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import xyz.srnyx.howdyholidays.HowdyHolidays;
import xyz.srnyx.howdyholidays.mongo.Profile;

import xyz.srnyx.lazylibrary.LazyEmbed;
import xyz.srnyx.lazylibrary.utility.LazyUtilities;

import java.util.ArrayList;
import java.util.List;


@CommandMarker
public class LeaderboardCmd extends ApplicationCommand {
    @Dependency private HowdyHolidays howdyHolidays;

    @JDASlashCommand(
            scope = CommandScope.GLOBAL,
            name = "leaderboard",
            description = "View the leaderboard of the users with the most presents!")
    public void commandLeaderboard(@NotNull GlobalSlashEvent event,
                                   @AppOption(description = "MANAGER-ONLY | Special leaderboard format for Erin") @Nullable Boolean erin) {
        if (erin == null) erin = false;
        if (erin && howdyHolidays.config.guild.roles.manager.checkDontHaveRole(event)) return;
        event.deferReply().queue();

        // Get sorted profiles
        final List<Profile> sorted = new ArrayList<>();
        howdyHolidays.mongo.getCollection(Profile.class).collection.aggregate(List.of(Aggregates.sort(Sorts.descending("presents")))).into(sorted);
        sorted.removeIf(profile -> profile.user == null);

        // Create embed
        final LazyEmbed embed = new LazyEmbed().setTitle(":trophy: Leaderboard");

        // Add user place
        final long userId = event.getUser().getIdLong();
        if (!erin) sorted.stream()
                .filter(profile -> userId == profile.user)
                .map(sorted::indexOf)
                .findFirst()
                .ifPresent(userPlace -> embed.setDescription("You're placed **#" + (userPlace + 1) + "** with **" + sorted.get(userPlace).getPresents() + "** :gift:"));

        // Create embeds
        final List<MessageEmbed> embeds = new ArrayList<>();
        final int size = sorted.size();
        for (int i = 0; i < size; i++) {
            final Profile profile = sorted.get(i);

            // Get emojis
            String emojis = switch (i) {
                case 0 -> " :first_place:";
                case 1 -> " :second_place:";
                case 2 -> " :third_place:";
                default -> "";
            };
            if (!erin && userId == profile.user) emojis += " :star:";

            // Add field
            embed.addField("#" + (i + 1) + emojis, "<@" + profile.user + "> with **" + profile.getPresents() + "** :gift:", false);

            // Erin, last/5th profile, send embed
            final boolean last = i == size - 1;
            if (erin && (last || i == 4)) {
                event.getHook().editOriginalEmbeds(embed.build(howdyHolidays)).queue();
                return;
            }

            // Last profile, add embed
            if (last) {
                embeds.add(embed.build(howdyHolidays));
                break;
            }

            // Every 10 profiles, add embed
            if (i % 10 == 9) {
                embeds.add(embed.build(howdyHolidays));
                embed.clearFields();
            }
        }

        // Pagination
        event.getHook().editOriginal(LazyUtilities.getDefaultPaginator()
                .useDeleteButton(false)
                .setMaxPages(embeds.size())
                .setConstraints(InteractionConstraints.ofUserIds(userId))
                .setPaginatorSupplier((paginator, editBuilder, components, page) -> embeds.get(page))
                .build().get()).queue();
    }
}
