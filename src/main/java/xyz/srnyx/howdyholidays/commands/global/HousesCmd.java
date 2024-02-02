package xyz.srnyx.howdyholidays.commands.global;

import com.freya02.botcommands.api.annotations.CommandMarker;
import com.freya02.botcommands.api.annotations.Dependency;
import com.freya02.botcommands.api.application.ApplicationCommand;
import com.freya02.botcommands.api.application.CommandScope;
import com.freya02.botcommands.api.application.annotations.AppOption;
import com.freya02.botcommands.api.application.slash.GlobalSlashEvent;
import com.freya02.botcommands.api.application.slash.annotations.JDASlashCommand;

import org.jetbrains.annotations.NotNull;

import org.jetbrains.annotations.Nullable;
import xyz.srnyx.howdyholidays.MessageUnion;
import xyz.srnyx.howdyholidays.config.HowdyConfig;
import xyz.srnyx.howdyholidays.HowdyHolidays;


@CommandMarker
public class HousesCmd extends ApplicationCommand {
    @Dependency private HowdyHolidays howdyHolidays;

    @JDASlashCommand(
            scope = CommandScope.GLOBAL,
            name = "houses",
            description = "MANAGER-ONLY | Start a new random houses round in the current channel")
    public void commandHouses(@NotNull GlobalSlashEvent event,
                              @AppOption(name = "high_risk", description = "Whether the houses should be high-risk") @Nullable Boolean highRisk) {
        final HowdyConfig.GuildNode guild = howdyHolidays.config.guild;
        if (guild.roles.manager.checkDontHaveRole(event)) return;
        if (guild.channels.general.id() == event.getChannel().getIdLong()) howdyHolidays.lastHouses = System.currentTimeMillis();
        howdyHolidays.sendHouses(new MessageUnion(event), highRisk);
    }
}
