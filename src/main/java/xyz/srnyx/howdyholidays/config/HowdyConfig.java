package xyz.srnyx.howdyholidays.config;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.interactions.components.selections.SelectOption;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.spongepowered.configurate.ConfigurationNode;

import xyz.srnyx.howdyholidays.HowdyHolidays;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class HowdyConfig {
    @NotNull private final HowdyHolidays howdyHolidays;
    @NotNull public final TimesNode times;
    @NotNull public final ActivityCheckNode activityCheck;
    public final int highRiskChance;
    public final double vipDailyMultiplier;
    @NotNull public final GuildNode guild;

    public HowdyConfig(@NotNull HowdyHolidays howdyHolidays) {
        this.howdyHolidays = howdyHolidays;

        final ConfigurationNode yaml = howdyHolidays.settings.fileSettings.file.yaml;
        this.times = new TimesNode(yaml.node("times"));
        this.activityCheck = new ActivityCheckNode(yaml.node("activity-check"));
        this.highRiskChance = yaml.node("high-risk-chance").getInt(10);
        this.vipDailyMultiplier = yaml.node("vip-daily-multiplier").getDouble(1.5);
        this.guild = new GuildNode(yaml.node("guild"));
    }

    public static class TimesNode {
        /**
         * seconds
         */
        public final long reactions;
        /**
         * milliseconds
         */
        public final long housesCooldown;

        public TimesNode(@NotNull ConfigurationNode node) {
            this.reactions = node.node("reactions").getLong();
            this.housesCooldown = node.node("houses-cooldown").getLong() * 60000;
        }
    }

    public static class ActivityCheckNode {
        /**
         * milliseconds
         */
        public final long cooldown;
        public final int requiredUsers;
        @NotNull public final MessagesNode messages;

        public ActivityCheckNode(@NotNull ConfigurationNode node) {
            this.cooldown = node.node("cooldown").getLong() * 1000;
            this.requiredUsers = node.node("required-users").getInt();
            this.messages = new MessagesNode(node.node("messages"));
        }

        public static class MessagesNode {
            /**
             * milliseconds
             */
            public final long oldest;
            public final int maximum;

            public MessagesNode(@NotNull ConfigurationNode node) {
                this.oldest = node.node("oldest").getLong() * 60000;
                this.maximum = node.node("maximum").getInt();
            }
        }
    }

    public class GuildNode {
        public final long id;
        @NotNull public final HousesNode houses;
        @NotNull public final String coal;
        @NotNull public final RolesNode roles;
        @NotNull public final ChannelsNode channels;

        public GuildNode(@NotNull ConfigurationNode node) {
            this.id = node.node("id").getLong();
            this.houses = new HousesNode(node.node("houses"));
            this.coal = node.node("coal").getString("<:coal:1181388428223455263>");
            this.roles = new RolesNode(node.node("roles"));
            this.channels = new ChannelsNode(node.node("channels"));
        }

        @Nullable
        public Guild getGuild() {
        return howdyHolidays.jda.getGuildById(id);
    }

        public static class HousesNode {
            @NotNull public final SelectOption one;
            @NotNull public final SelectOption two;
            @NotNull public final SelectOption three;

            @NotNull public final List<SelectOption> all;

            public HousesNode(@NotNull ConfigurationNode node) {
                final ConfigurationNode oneNode = node.node("one");
                final ConfigurationNode twoNode = node.node("two");
                final ConfigurationNode threeNode = node.node("three");

                this.one = SelectOption.of(oneNode.node("label").getString("Gold"), "one").withEmoji(Emoji.fromFormatted(oneNode.node("emoji").getString("<:housegold:1172317682956259358>")));
                this.two = SelectOption.of(twoNode.node("label").getString("Green"), "two").withEmoji(Emoji.fromFormatted(twoNode.node("emoji").getString("<:housegreen:1172317681660207206>")));
                this.three = SelectOption.of(threeNode.node("label").getString("Red"), "three").withEmoji(Emoji.fromFormatted(threeNode.node("emoji").getString("<:housered:1172317679944745076>")));

                this.all = List.of(one, two, three);
            }

            @NotNull
            public String getOneEmoji() {
                final EmojiUnion emoji = one.getEmoji();
                return emoji == null ? "<:housegold:1172317682956259358>" : emoji.getFormatted();
            }

            @NotNull
            public String getTwoEmoji() {
                final EmojiUnion emoji = two.getEmoji();
                return emoji == null ? "<:housegreen:1172317681660207206>" : emoji.getFormatted();
            }

            @NotNull
            public String getThreeEmoji() {
                final EmojiUnion emoji = three.getEmoji();
                return emoji == null ? "<:housered:1172317679944745076>" : emoji.getFormatted();
            }
        }

        public class RolesNode {
            @NotNull public final HowdyRole manager;
            @NotNull public final Set<HowdyRole> vip = new HashSet<>();

            public RolesNode(@NotNull ConfigurationNode node) {
                this.manager = new HowdyRole(howdyHolidays, node.node("manager").getLong());
                node.node("vip").childrenList().stream()
                        .map(vipNode -> new HowdyRole(howdyHolidays, vipNode.getLong()))
                        .forEach(this.vip::add);
            }

            public boolean isVip(long userId) {
                final Guild jdaGuild = getGuild();
                if (jdaGuild == null) return false;
                final Member member = jdaGuild.retrieveMemberById(userId).complete();
                return vip.stream().anyMatch(role -> role.hasRole(member));
            }
        }

        public class ChannelsNode {
            @NotNull public final HowdyChannel general;

            public ChannelsNode(@NotNull ConfigurationNode node) {
                this.general = new HowdyChannel(howdyHolidays, node.node("general").getLong());
            }
        }
    }
}
