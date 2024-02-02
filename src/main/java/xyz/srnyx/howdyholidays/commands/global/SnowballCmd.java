package xyz.srnyx.howdyholidays.commands.global;

import com.freya02.botcommands.api.annotations.CommandMarker;
import com.freya02.botcommands.api.annotations.Dependency;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.CommandScope;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GlobalSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;
import com.freya02.botcommands.api.application.slash.annotations.LongRange;
import com.freya02.botcommands.api.components.Components;
import com.freya02.botcommands.api.components.event.ButtonEvent;
import com.freya02.botcommands.api.utils.ButtonContent;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import xyz.srnyx.howdyholidays.HowdyHolidays;
import xyz.srnyx.howdyholidays.mongo.Profile;

import xyz.srnyx.lazylibrary.LazyEmbed;
import xyz.srnyx.lazylibrary.LazyEmoji;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;


@CommandMarker
public class SnowballCmd extends ApplicationCommand {
    private static final int ACCEPT_TIME = 45; // seconds
    private static final int DELAY = 15; // seconds

    @Dependency private HowdyHolidays howdyHolidays;
    @NotNull private final Map<Long, String> currentBattles = new HashMap<>();

    @JDASlashCommand(
            scope = CommandScope.GLOBAL,
            name = "snowball",
            description = "Start a snowball fight with another user!")
    public void snowballCmd(@NotNull GlobalSlashEvent event,
                            @AppOption(description = "The user to snowball fight with") @NotNull User user,
                            @AppOption(description = "The amount of presents to bet") @LongRange(from = 1, to = 100) int amount) {
        if (user.getIdLong() == event.getUser().getIdLong()) {
            event.reply(LazyEmoji.NO + " You can't snowball fight yourself!").setEphemeral(true).queue();
            return;
        }

        startSnowballFight(event, event.getUser(), user, amount);
    }

    @NotNull private static final UnicodeEmoji SNOWFLAKE = Emoji.fromUnicode("❄");

    private void startSnowballFight(@NotNull IReplyCallback event, @NotNull User author, @NotNull User target, int amount) {
        final long authorId = author.getIdLong();
        final long targetId = target.getIdLong();

        // Check if in battle
        final String authorBattle = currentBattles.get(authorId);
        if (authorBattle != null) {
            event.reply(LazyEmoji.NO + " You're already in a battle!")
                    .setActionRow(Button.link(authorBattle, "Go to battle").withEmoji(SNOWFLAKE))
                    .setEphemeral(true).queue();
            return;
        }
        final String targetBattle = currentBattles.get(targetId);
        if (targetBattle != null) {
            event.reply(LazyEmoji.NO + " " + target.getAsMention() + " is already in a battle!")
                    .setActionRow(Button.link(targetBattle, "Go to battle").withEmoji(SNOWFLAKE))
                    .setEphemeral(true).queue();
            return;
        }

        // Check self profile
        final Profile profile = howdyHolidays.mongo.getCollection(Profile.class).findOne("user", authorId);
        if (profile == null) {
            event.reply(LazyEmoji.NO + " You don't have any presents to bet!").setEphemeral(true).queue();
            return;
        }
        if (profile.getPresents() < amount) {
            event.reply(LazyEmoji.NO + " You don't have enough presents to bet that much!").setEphemeral(true).queue();
            return;
        }

        // Check target profile
        final String targetMention = target.getAsMention();
        final Profile targetProfile = howdyHolidays.mongo.getCollection(Profile.class).findOne("user", targetId);
        if (targetProfile == null) {
            event.reply(LazyEmoji.NO + " " + targetMention + " doesn't have any presents to bet!").setEphemeral(true).queue();
            return;
        }
        if (targetProfile.getPresents() < amount) {
            event.reply(LazyEmoji.NO + " " + targetMention + " doesn't have enough presents to bet that much!").setEphemeral(true).queue();
            return;
        }

        // Send duel request message
        final AtomicBoolean decided = new AtomicBoolean(false);
        final LazyEmbed embed = new LazyEmbed()
                .setTitle(":snowflake: Snowball Fight! " + LazyEmoji.WARNING_CLEAR)
                .setDescription("A new battle has been initiated!\n**Do you accept the challenge " + targetMention + "?**\n*Auto-declining <t:" + ((System.currentTimeMillis() / 1000) + ACCEPT_TIME) + ":R>*")
                .setColor(Color.ORANGE)
                .addField("Challenger", author.getAsMention(), true)
                .addField("Opponent", targetMention, true)
                .addField("Bet", amount + " :gift:", true);
        event.reply(targetMention).setEmbeds(embed.build(howdyHolidays)).setActionRow(
                Components.successButton(buttonEvent -> {
                    if (buttonEvent.getUser().getIdLong() != targetId) {
                        buttonEvent.reply(LazyEmoji.NO + " Only the opponent can accept/decline!").setEphemeral(true).queue();
                        return;
                    }
                    askForHidingLocations(buttonEvent, new Data(authorId, targetId, amount, embed));
                    decided.set(true);

                    // Add to currentBattles
                    final String battleUrl = buttonEvent.getMessage().getJumpUrl();
                    currentBattles.put(authorId, battleUrl);
                    currentBattles.put(targetId, battleUrl);
                }).build(LazyEmoji.YES_CLEAR.getButtonContent("Accept")),
                Components.dangerButton(buttonEvent -> {
                    if (buttonEvent.getUser().getIdLong() != targetId) {
                        buttonEvent.reply(LazyEmoji.NO + " Only the opponent can accept/decline!").setEphemeral(true).queue();
                        return;
                    }
                    buttonEvent.editMessage("")
                            .setEmbeds(embed
                                    .setTitle(":snowflake: Snowball Fight! " + LazyEmoji.NO_CLEAR)
                                    .setDescription(targetMention + " has declined the challenge!")
                                    .setColor(Color.RED).build(howdyHolidays))
                            .setComponents().queue();
                    decided.set(true);
                }).build(LazyEmoji.NO_CLEAR_DARK.getButtonContent("Decline"))).queue();

        // Start 30-second timer
        howdyHolidays.executor_service.schedule(() -> {
            if (!decided.get()) event.getHook().editOriginalEmbeds(embed
                            .setTitle(":snowflake: Snowball Fight! " + LazyEmoji.NO_CLEAR)
                            .setDescription(targetMention + " did not respond in time!")
                            .setColor(Color.RED).build(howdyHolidays))
                    .setComponents().queue();
        }, ACCEPT_TIME, TimeUnit.SECONDS);
    }

    @NotNull private static final List<SelectOption> HIDING_OPTIONS = List.of(SelectOption.of("Hide behind pile A!", "a").withEmoji(Emoji.fromUnicode("\uD83C\uDDE6")), SelectOption.of("Hide behind pile B!", "b").withEmoji(Emoji.fromUnicode("\uD83C\uDDE7")));

    private void askForHidingLocations(@NotNull ButtonEvent event, @NotNull Data data) {
        event.editMessage("")
                .setEmbeds(data.embed
                        .setTitle(":gift: Snowball Fight! " + LazyEmoji.MAYBE_CLEAR)
                        .setDescription("<@" + data.targetId + "> has accepted the challenge!\n**Both players, please select a present pile you'd like to hide behind :)**\n*Auto-selecting <t:" + ((System.currentTimeMillis() / 1000) + DELAY) + ":R>*")
                        .setColor(Color.GREEN).build(howdyHolidays))
                .setActionRow(Components.stringSelectionMenu(hideEvent -> {
                    final long userId = hideEvent.getUser().getIdLong();
                    final String selected = hideEvent.getValues().get(0);
                    if (data.isAuthor(userId)) {
                        data.authorHiding = selected;
                    } else if (data.isTarget(userId)) {
                        data.targetHiding = selected;
                    } else {
                        hideEvent.reply(LazyEmoji.NO + " You're not a participant in this challenge!").setEphemeral(true).queue();
                        return;
                    }
                    hideEvent.deferEdit().queue();
                })
                        .setMaxValues(1)
                        .setPlaceholder("Select a present pile to HIDE behind!")
                        .addOptions(HIDING_OPTIONS)
                        .build()).queue();

        howdyHolidays.executor_service.schedule(() -> askForHitLocations(event.getHook(), data), DELAY, TimeUnit.SECONDS);
    }

    @NotNull private static final List<SelectOption> HIT_OPTIONS = List.of(SelectOption.of("Hit pile A!", "a").withEmoji(Emoji.fromUnicode("\uD83C\uDDE6")), SelectOption.of("Hit pile B!", "b").withEmoji(Emoji.fromUnicode("\uD83C\uDDE7")));

    private void askForHitLocations(@NotNull InteractionHook hook, @NotNull Data data) {
        hook.editOriginalEmbeds(data.embed
                        .setTitle(":anger: Snowball Fight! " + LazyEmoji.MAYBE_CLEAR)
                        .setDescription("**Both players, please select a present pile you'd like to hit :O**\n*Auto-selecting <t:" + ((System.currentTimeMillis() / 1000) + DELAY) + ":R>*")
                        .setColor(Color.RED).build(howdyHolidays))
                .setActionRow(Components.stringSelectionMenu(hitEvent -> {
                    final long userId = hitEvent.getUser().getIdLong();
                    final String selected = hitEvent.getValues().get(0);
                    if (data.isAuthor(userId)) {
                        data.authorHit = selected;
                    } else if (data.isTarget(userId)) {
                        data.targetHit = selected;
                    } else {
                        hitEvent.reply(LazyEmoji.NO + " You're not a participant in this challenge!").setEphemeral(true).queue();
                        return;
                    }
                    hitEvent.deferEdit().queue();
                })
                        .setMaxValues(1)
                        .setPlaceholder("Select a present pile to HIT!")
                        .addOptions(HIT_OPTIONS)
                        .build()).queue();

        howdyHolidays.executor_service.schedule(() -> sendResults(hook, data), DELAY, TimeUnit.SECONDS);
    }

    private void sendResults(@NotNull InteractionHook hook, @NotNull Data data) {
        // Get locations
        Location authorHiding = Location.fromString(data.authorHiding);
        Location targetHiding = Location.fromString(data.targetHiding);
        Location authorHit = Location.fromString(data.authorHit);
        Location targetHit = Location.fromString(data.targetHit);
        if (authorHiding == null) authorHiding = Location.getRandom();
        if (targetHiding == null) targetHiding = Location.getRandom();
        if (authorHit == null) authorHit = Location.getRandom();
        if (targetHit == null) targetHit = Location.getRandom();

        // Get winner/loser
        long winnerId;
        long loserId;
        final Location loserHiding;
        boolean targetHitAuthor = authorHiding.equals(targetHit);
        boolean authorHitTarget = targetHiding.equals(authorHit);
        if ((targetHitAuthor && authorHitTarget) || (!targetHitAuthor && !authorHitTarget)) {
            hook.editOriginalEmbeds(data.embed
                            .setTitle(":snowflake: Snowball Fight! " + LazyEmoji.YES_CLEAR)
                            .setDescription("**The results are in!** Looks like it was a tie, no one won or lost any presents :D")
                            .setColor(Color.YELLOW).build(howdyHolidays))
                .setActionRow(getRematchButton(data))
                .queue();
            currentBattles.remove(data.authorId);
            currentBattles.remove(data.targetId);
            return;
        } else if (targetHitAuthor) {
            // Author wins
            winnerId = data.targetId;
            loserId = data.authorId;
            loserHiding = authorHiding;
        } else {
            // Target wins
            winnerId = data.authorId;
            loserId = data.targetId;
            loserHiding = targetHiding;
        }

        // Update profiles
        final Profile winnerProfile = howdyHolidays.mongo.getCollection(Profile.class).findOneAndUpsert(Filters.eq("user", winnerId), Updates.inc("presents", data.amount));
        final Profile loserProfile = howdyHolidays.mongo.getCollection(Profile.class).findOneAndUpsert(Filters.eq("user", loserId), Updates.inc("presents", -data.amount));

        // Send results
        final String winnerMention = "<@" + winnerId + ">";
        final String loserMention = "<@" + loserId + ">";
        final int winnerPresents = winnerProfile == null ? 0 : winnerProfile.getPresents();
        final int loserPresents = loserProfile == null ? 0 : loserProfile.getPresents();
        final LazyEmbed embed = data.embed
                .setTitle(":snowflake: Snowball Fight! " + LazyEmoji.YES_CLEAR)
                .setDescription("**The results are in!** :anger:\n" + winnerMention + " hit " + loserMention + " behind **" + loserHiding.toDisplayString() + "**!")
                .setColor(Color.GREEN)
                .clearFields()
                .addField(LazyEmoji.YES + " Winner", "<@" + winnerId + "> won **" + data.amount + " :gift:**\nThey now have **" + winnerPresents + " :gift:**", false)
                .addField(LazyEmoji.NO + " Loser", "<@" + loserId + "> lost **" + data.amount + " :gift:**\nThey now have **" + loserPresents + " :gift:**", false);
        hook.editOriginalEmbeds(embed.build(howdyHolidays))
                .setActionRow(getRematchButton(data))
                .queue();
        currentBattles.remove(data.authorId);
        currentBattles.remove(data.targetId);
    }

    @NotNull private static final ButtonContent REMATCH = ButtonContent.withEmoji("Rematch!", Emoji.fromUnicode("\uD83D\uDD03"));

    @NotNull
    private Button getRematchButton(@NotNull Data data) {
        return Components.secondaryButton(buttonEvent -> {
            final User clicker = buttonEvent.getUser();
            final long clickerId = clicker.getIdLong();
            final boolean isUser1 = data.isAuthor(clickerId);
            if (!isUser1 && !data.isTarget(clickerId)) {
                buttonEvent.reply(LazyEmoji.NO + " You weren't a participant in this challenge!").setEphemeral(true).queue();
                return;
            }
            startSnowballFight(buttonEvent, clicker, isUser1 ? data.getTarget() : data.getAuthor(), data.amount);
        }).build(REMATCH);
    }

    private class Data {
        private final long authorId;
        private final long targetId;
        private final int amount;
        @NotNull private final LazyEmbed embed;
        @Nullable private String authorHiding;
        @Nullable private String targetHiding;
        @Nullable private String authorHit;
        @Nullable private String targetHit;

        public Data(long authorId, long targetId, int amount, @NotNull LazyEmbed embed) {
            this.authorId = authorId;
            this.targetId = targetId;
            this.amount = amount;
            this.embed = embed;
        }

        @NotNull
        private User getAuthor() {
            return howdyHolidays.jda.retrieveUserById(authorId).complete();
        }

        @NotNull
        private User getTarget() {
            return howdyHolidays.jda.retrieveUserById(targetId).complete();
        }

        private boolean isAuthor(long id) {
            return authorId == id;
        }

        private boolean isTarget(long id) {
            return targetId == id;
        }
    }

    private enum Location {
        A,
        B;

        @NotNull
        private String toDisplayString() {
            return "Present Pile " + toString().toUpperCase();
        }

        @NotNull private static final List<Location> LOCATIONS = List.of(A, B);

        @Nullable
        public static Location fromString(@Nullable String string) {
            if (string == null) return null;
            try {
                return valueOf(string.toUpperCase());
            } catch (final IllegalArgumentException e) {
                return null;
            }
        }

        @NotNull
        private static Location getRandom() {
            return LOCATIONS.get(HowdyHolidays.RANDOM.nextInt(2));
        }
    }
}
