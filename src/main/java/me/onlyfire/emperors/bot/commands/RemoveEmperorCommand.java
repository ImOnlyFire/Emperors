package me.onlyfire.emperors.bot.commands;

import me.onlyfire.emperors.bot.EmperorsBot;
import me.onlyfire.emperors.bot.commands.api.MessagedBotCommand;
import me.onlyfire.emperors.bot.EmperorException;
import me.onlyfire.emperors.bot.database.EmperorsDatabase;
import me.onlyfire.emperors.bot.Language;
import me.onlyfire.emperors.utils.MemberUtils;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class RemoveEmperorCommand extends MessagedBotCommand {

    private final EmperorsBot emperorsBot;

    public RemoveEmperorCommand(EmperorsBot emperorsBot) {
        super("removeemperor", "Rimuove un imperatore dal gruppo");
        this.emperorsBot = emperorsBot;
    }

    @Override
    public void execute(AbsSender absSender, User user, Message message, Chat chat, String[] strings) {
        if (chat.isUserChat() || MemberUtils.isNormalUser(absSender, user, chat))
            return;

        SendMessage sendMessage = new SendMessage();
        sendMessage.enableHtml(true);
        sendMessage.setChatId(String.valueOf(chat.getId()));
        sendMessage.setReplyToMessageId(message.getMessageId());

        if (emperorsBot.getUserMode().containsKey(user))
            emperorsBot.removeUserMode(user, chat, null);

        if (strings.length == 0) {
            sendMessage.setText(Language.REMOVE_EMPEROR_USAGE.toString());
            try {
                absSender.execute(sendMessage);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            return;
        }

        String emperorName = String.join(" ", strings).replace(strings[0] + " ", "").toLowerCase();
        EmperorsDatabase database = emperorsBot.getDatabase();

        database.getEmperor(message.getChatId(), emperorName).whenComplete((emperor, exception) -> {
            if (exception != null) {
                emperorsBot.generateErrorMessage(chat, new EmperorException("Errore nel database", exception));
                return;
            }
            if (emperor == null) {
                sendMessage.setText(Language.NOT_EXIST_EMPEROR.toString());
                try {
                    absSender.execute(sendMessage);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
                return;
            }

            sendMessage.setText(String.format(Language.REMOVED_EMPEROR_SUCCESSFULLY.toString(), emperorName));
            database.deleteEmperor(emperorName, chat);
            emperorsBot.getLogger().info(String.format(Language.REMOVED_LOG.toString(), emperorName, chat.getId(), chat.getTitle()));

            try {
                absSender.execute(sendMessage);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        });

    }

}
