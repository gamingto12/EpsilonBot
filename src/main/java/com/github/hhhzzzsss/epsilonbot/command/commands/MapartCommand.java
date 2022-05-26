package com.github.hhhzzzsss.epsilonbot.command.commands;

import com.github.hhhzzzsss.epsilonbot.EpsilonBot;
import com.github.hhhzzzsss.epsilonbot.block.Section;
import com.github.hhhzzzsss.epsilonbot.buildsync.PlotManager;
import com.github.hhhzzzsss.epsilonbot.buildsync.PlotRepairSession;
import com.github.hhhzzzsss.epsilonbot.command.ArgsParser;
import com.github.hhhzzzsss.epsilonbot.command.ChatCommand;
import com.github.hhhzzzsss.epsilonbot.command.ChatSender;
import com.github.hhhzzzsss.epsilonbot.command.CommandException;
import com.github.hhhzzzsss.epsilonbot.mapart.MapartBuilderSession;
import com.github.hhhzzzsss.epsilonbot.mapart.MapartManager;
import com.github.hhhzzzsss.epsilonbot.modules.BuildHandler;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.util.Map;

@RequiredArgsConstructor
public class MapartCommand implements ChatCommand {

    private final EpsilonBot bot;

    @Override
    public String getName() {
        return "mapart";
    }
    @Override
    public String[] getSyntax() {
        return new String[] {
                "<url> [<width>] [<height>]",
        };
    }
    @Override
    public String getDescription() {
        return "Builds mapart for a given image";
    }
    @Override
    public int getPermission() {
        return 0;
    }

    @Override
    public void executeChat(ChatSender sender, String args) throws CommandException {
        ArgsParser parser = new ArgsParser(this, args);

        String url = parser.readWord(true);
        Integer width = parser.readInt(false);
        Integer height = parser.readInt(false);
        if (width == null) {
            width = 1;
            height = 1;
        } else if (height == null) {
            throw new CommandException("If you specify a width please specify a height as well");
        }

        if (width < 1 || height < 1) {
            throw new CommandException("Width and height must be positive");
        }

        if (sender.getPermission() == 0) {
            if (width > 3 || height > 3) {
                throw new CommandException("Width and height cannot exceed 3");
            }
        }

        BuildHandler buildHandler = bot.getBuildHandler();
        if (buildHandler.getBuilderSession() != null) {
            throw new CommandException("I'm currently working on another build so I can't start something else");
        }
        int mapIdx = MapartManager.getMapartIndex().size();
        try {
            buildHandler.setBuilderSession(new MapartBuilderSession(bot, mapIdx, url, width, height));
        } catch (IOException e) {
            throw new CommandException(e.getMessage());
        }
        bot.sendChat("Loading mapart...");
    }
}
