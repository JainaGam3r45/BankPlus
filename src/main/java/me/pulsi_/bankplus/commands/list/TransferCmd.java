package me.pulsi_.bankplus.commands.list;

import me.pulsi_.bankplus.BankPlus;
import me.pulsi_.bankplus.commands.BPCmdExecution;
import me.pulsi_.bankplus.commands.BPCommand;
import me.pulsi_.bankplus.economy.BPEconomy;
import me.pulsi_.bankplus.sql.BPSQL;
import me.pulsi_.bankplus.utils.BPLogger;
import me.pulsi_.bankplus.utils.texts.BPArgs;
import me.pulsi_.bankplus.utils.texts.BPFormatter;
import me.pulsi_.bankplus.utils.texts.BPMessages;
import me.pulsi_.bankplus.values.ConfigValues;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TransferCmd extends BPCommand {

    public TransferCmd(FileConfiguration commandsConfig, String commandID) {
        super(commandsConfig, commandID);
    }

    public TransferCmd(FileConfiguration commandsConfig, String commandID, String... aliases) {
        super(commandsConfig, commandID, aliases);
    }

    @Override
    public List<String> defaultUsage() {
        return Arrays.asList(
                "%prefix% Usage: /bank transfer [mode]",
                "Specify a mode between <aqua>\"filesToDatabase\"</aqua> and <aqua>\"databaseToFiles\"</aqua>.",
                "Use this command to transfer the playerdata from a place to another in case you switch saving mode."
        );
    }

    @Override
    public int defaultConfirmCooldown() {
        return 5;
    }

    @Override
    public List<String> defaultConfirmMessage() {
        return Collections.singletonList("%prefix% <red>This command will overwrite the data from a place to another, type the command again within 5 seconds to confirm.");
    }

    @Override
    public int defaultCooldown() {
        return 0;
    }

    @Override
    public List<String> defaultCooldownMessage() {
        return Collections.emptyList();
    }

    @Override
    public boolean playerOnly() {
        return false;
    }

    @Override
    public boolean skipUsage() {
        return false;
    }

    @Override
    public BPCmdExecution onExecution(CommandSender s, String[] args) {
        String mode = args[1].toLowerCase();

        if (!mode.equals("filestodatabase") && !mode.equals("databasetofiles")) {
            BPMessages.sendIdentifier(s, "Invalid-Action");
            return BPCmdExecution.invalidExecution();
        }

        if (BPSQL.getConnection() == null) {
            BPMessages.sendMessage(s, "%prefix% <red>Could not initialize the task, the database is not connected!");
            return BPCmdExecution.invalidExecution();
        }

        return new BPCmdExecution() {
            @Override
            public void execute() {
                BPMessages.sendMessage(s, "%prefix% Task initialized, wait a few moments...");

                Bukkit.getScheduler().runTaskAsynchronously(BankPlus.INSTANCE(), () -> {
                    if (args[1].equalsIgnoreCase("filestodatabase")) localToExternal();
                    else externalToLocal();

                    BPMessages.sendMessage(s, "%prefix% Task finished!");
                });
            }
        };
    }

    @Override
    public List<String> tabCompletion(CommandSender s, String[] args) {
        if (args.length == 2)
            return BPArgs.getArgs(args, "databaseToFiles", "filesToDatabase");
        return null;
    }

    private void localToExternal() {
        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
            File file = getPlayerFile(p);
            if (!file.exists()) continue;

            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            for (BPEconomy economy : BPEconomy.list()) {
                String bankName = economy.getOriginBank().getIdentifier();
                String path = "banks." + bankName;
                if (!config.contains(path)) continue;

                BigDecimal debt = BPFormatter.getStyledBigDecimal(config.getString(path + ".debt"));
                BigDecimal money = BPFormatter.getStyledBigDecimal(config.getString(path + ".money"));
                BigDecimal interest = BPFormatter.getStyledBigDecimal(config.getString(path + ".interest"));
                int level = config.getInt(path + ".level", 1);

                BPSQL.setDebt(p, bankName, debt);
                BPSQL.setMoney(p, bankName, money);
                BPSQL.setInterest(p, bankName, interest);
                BPSQL.setBankLevel(p, bankName, BigDecimal.valueOf(level));
            }
        }
    }

    private void externalToLocal() {
        File folder = new File(BankPlus.INSTANCE().getDataFolder(), "playerdata");
        if (!folder.exists()) folder.mkdirs();

        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
            if (!BPSQL.isRegistered(p, ConfigValues.getMainGuiName())) continue;

            File file = getPlayerFile(p);
            FileConfiguration config = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
            if (p.getName() != null) config.set("name", p.getName());

            for (String bankName : BPEconomy.nameList()) {
                config.set("banks." + bankName + ".debt", BPSQL.getDebt(p, bankName).toPlainString());
                config.set("banks." + bankName + ".interest", BPSQL.getInterest(p, bankName).toPlainString());
                config.set("banks." + bankName + ".level", BPSQL.getBankLevel(p, bankName));
                config.set("banks." + bankName + ".money", BPSQL.getMoney(p, bankName).toPlainString());
            }

            try {
                config.save(file);
            } catch (IOException e) {
                BPLogger.Console.error(e, "Could not save player file for " + p.getName() + ".");
            }
        }
    }

    private File getPlayerFile(OfflinePlayer p) {
        String id = ConfigValues.isStoringUUIDs() ? p.getUniqueId().toString() : p.getName();
        return new File(BankPlus.INSTANCE().getDataFolder(), "playerdata" + File.separator + id + ".yml");
    }
}
