package com.agrilink.notification;

import com.agrilink.user.Language;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/** Renders localised (English / Amharic / Afaan Oromoo) notification and SMS text from message bundles. */
@Component
public class NotificationMessages {

    private final MessageSource messages;

    public NotificationMessages(@Qualifier("notificationMessageSource") MessageSource messages) {
        this.messages = messages;
    }

    public String render(Language language, String key, Object... args) {
        return messages.getMessage(key, args, language.locale());
    }
}
