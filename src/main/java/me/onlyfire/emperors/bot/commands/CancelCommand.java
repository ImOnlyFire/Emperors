package me.onlyfire.emperors.bot.commands;

import me.onlyfire.emperors.bot.EmperorsBot;
import me.onlyfire.emperors.bot.commands.api.MessagedBotCommand;
import me.onlyfire.emperors.user.EmperorUserMode;
import me.onlyfire.emperors.utils.MemberUtils;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class CancelCommand extends MessagedBotCommand {

    private final EmperorsBot emperorsBot;

    public CancelCommand(EmperorsBot emperorsBot) {
        super("cancel", "Cancella l'azione in corso.");
        this.emperorsBot = emperorsBot;
    }

    @Override
    public void execute(AbsSender absSender, User user, Message message, Chat chat, String[] strings) {
        if (!MemberUtils.isAdministrator(absSender, user, chat))
            return;

        if (emperorsBot.userMode.containsKey(user)) {
            EmperorUserMode userTask = emperorsBot.userMode.get(user);

            if (userTask == null)
                return;
            if (!userTask.getChat().getId().equals(chat.getId()))
                return;

            userTask.stop();
            emperorsBot.userMode.remove(user);

            SendMessage sendMessage = new SendMessage();
            sendMessage.enableHtml(true);
            sendMessage.setChatId(String.valueOf(chat.getId()));

            sendMessage.setText("Rimosso dalla modalità corrente.");

            try {
                absSender.executeAsync(sendMessage);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }

        }
    }
}
