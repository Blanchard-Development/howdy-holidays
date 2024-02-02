package xyz.srnyx.howdyholidays;

import com.freya02.botcommands.api.application.slash.GlobalSlashEvent;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.ItemComponent;
import net.dv8tion.jda.api.requests.RestAction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class MessageUnion {
    @Nullable private final TextChannel channel;
    @Nullable private final GlobalSlashEvent event;
    @Nullable private Long messageId;

    public MessageUnion(@NotNull TextChannel channel) {
        this.channel = channel;
        this.event = null;
    }
    
    public MessageUnion(@NotNull GlobalSlashEvent event) {
        this.channel = null;
        this.event = event;
    }

    private boolean isChannel() {
        return channel != null;
    }

    private boolean isEvent() {
        return event != null;
    }

    @NotNull
    public RestAction<Message> sendMessage(@NotNull MessageEmbed embed, @NotNull ItemComponent component) {
        if (isChannel()) return channel.sendMessageEmbeds(embed).setActionRow(component).map(sentMessage -> {
            messageId = sentMessage.getIdLong();
            return sentMessage;
        });
        if (isEvent()) return event.replyEmbeds(embed).setActionRow(component).complete().retrieveOriginal();
        throw new IllegalStateException("MessageUnion is not a channel or event");
    }

    @Nullable
    public Message getMessage() {
        if (isChannel()) {
            if (messageId == null) return null;
            return channel.retrieveMessageById(messageId).complete();
        }
        if (isEvent()) return event.getHook().retrieveOriginal().complete();
        return null;
    }

    @NotNull
    public RestAction<?> editMessage(@NotNull String message) {
        if (isChannel()) {
            if (messageId == null) throw new IllegalStateException("MessageUnion has not sent a message");
            return channel.retrieveMessageById(messageId).complete().editMessage(message).setComponents();
        }
        if (isEvent()) return event.getHook().editOriginal(message).setComponents();
        throw new IllegalStateException("MessageUnion is not a channel or event");
    }

    @NotNull
    public RestAction<?> editEmbed(@NotNull MessageEmbed embed) {
        if (isChannel()) {
            if (messageId == null) throw new IllegalStateException("MessageUnion has not sent a message");
            return channel.retrieveMessageById(messageId).complete().editMessageEmbeds(embed).setComponents();
        }
        if (isEvent()) return event.getHook().editOriginalEmbeds(embed).setComponents();
        throw new IllegalStateException("MessageUnion is not a channel or event");
    }
}
