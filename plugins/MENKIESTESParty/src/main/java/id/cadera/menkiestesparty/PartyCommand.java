package id.cadera.menkiestesparty;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PartyCommand implements CommandExecutor, TabCompleter {
    private final MENKIESTESPartyPlugin plugin;
    private final PartyService parties;

    public PartyCommand(MENKIESTESPartyPlugin plugin, PartyService parties) {
        this.plugin = plugin;
        this.parties = parties;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("pchat")) return pchat(sender, args);
        if (cmd.equals("partywar")) return partyWar(sender, args);
        if (cmd.equals("partyseason")) return partySeason(sender, args);
        if (cmd.equals("partyhall")) return partyHall(sender, args);
        return party(sender, args);
    }

    private boolean party(CommandSender sender, String[] a) {
        if (a.length == 0) {
            if (sender instanceof Player p) parties.openMenu(p);
            else sender.sendMessage("/party help");
            return true;
        }

        String sub = a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("help")) {
            help(sender);
            return true;
        }
        if (!(sender instanceof Player p) && !isAdminSub(sub)) {
            sender.sendMessage("Player only.");
            return true;
        }

        Player p = sender instanceof Player ? (Player) sender : null;
        switch (sub) {
            case "menu" -> parties.openMenu(p);
            case "create" -> {
                if (a.length < 2) p.sendMessage(parties.prefix() + Util.color(" &c/party create <nama>"));
                else parties.create(p, a[1]);
            }
            case "invite" -> {
                if (a.length < 2) {
                    p.sendMessage(parties.prefix() + Util.color(" &c/party invite <player>"));
                    break;
                }
                Player t = Bukkit.getPlayer(a[1]);
                if (t == null) p.sendMessage(parties.prefix() + Util.color(" &cPlayer tidak online."));
                else parties.invite(p, t);
            }
            case "accept" -> parties.accept(p);
            case "leave" -> parties.leave(p);
            case "disband" -> parties.disband(p);
            case "kick" -> {
                if (a.length < 2) {
                    p.sendMessage(parties.prefix() + Util.color(" &c/party kick <player>"));
                    break;
                }
                OfflinePlayer t = Bukkit.getOfflinePlayer(a[1]);
                parties.kick(p, t);
            }
            case "promote" -> {
                if (a.length < 2) {
                    p.sendMessage(parties.prefix() + Util.color(" &c/party promote <player>"));
                    break;
                }
                parties.setRole(p, Bukkit.getOfflinePlayer(a[1]), PartyService.Role.OFFICER);
            }
            case "demote" -> {
                if (a.length < 2) {
                    p.sendMessage(parties.prefix() + Util.color(" &c/party demote <player>"));
                    break;
                }
                parties.setRole(p, Bukkit.getOfflinePlayer(a[1]), PartyService.Role.MEMBER);
            }
            case "sethome" -> parties.setHome(p);
            case "home" -> parties.home(p);
            case "members" -> showMembers(p);
            case "contribution", "contrib" -> parties.showContribution(p);
            case "rep" -> showRep(p);
            case "level" -> showLevel(p);
            case "quest", "quests" -> showQuest(p);
            case "relic" -> showRelic(p);
            case "top" -> showTop(p);
            case "war" -> {
                if (a.length >= 2 && a[1].equalsIgnoreCase("hunt")) plugin.war().hunt(p);
                else if (a.length >= 2 && a[1].equalsIgnoreCase("top")) plugin.war().showTop(p);
                else plugin.war().showStatus(p);
            }
            case "claimchest", "rewards" -> parties.claimWarChest(p);
            case "hall" -> handleHallPlayer(p, dropFirst(a));
            case "addrep", "removerep", "setrep", "resetquest", "reload" -> adminParty(sender, sub, a);
            default -> p.sendMessage(parties.prefix() + Util.color(" &cSubcommand tidak dikenal. /party help"));
        }
        return true;
    }

    private String[] dropFirst(String[] input) {
        if (input.length <= 1) return new String[0];
        String[] out = new String[input.length - 1];
        System.arraycopy(input, 1, out, 0, out.length);
        return out;
    }

    private boolean isAdminSub(String s) {
        return List.of("addrep", "removerep", "setrep", "resetquest", "reload").contains(s);
    }

    private void adminParty(CommandSender s, String sub, String[] a) {
        if (!s.hasPermission("menkiestesparty.admin")) {
            s.sendMessage(Util.color("&cNo permission."));
            return;
        }
        if (sub.equals("reload")) {
            plugin.reloadPluginConfig();
            s.sendMessage(Util.color("&aMENKIESTESParty config reloaded."));
            return;
        }
        if (sub.equals("resetquest")) {
            parties.resetWeeklyQuests(true);
            return;
        }
        if (a.length < 3) {
            s.sendMessage("/party " + sub + " <party> <amount>");
            return;
        }
        String key = Util.key(a[1]);
        if (!parties.exists(key)) {
            s.sendMessage("Party not found.");
            return;
        }
        int val = Util.parseInt(a[2], 0, -100000000, 100000000);
        if (sub.equals("setrep")) {
            int delta = val - parties.rep(key);
            parties.addRep(key, delta);
        } else if (sub.equals("removerep")) parties.addRep(key, -Math.abs(val));
        else parties.addRep(key, Math.abs(val));
        plugin.saveDataSoon();
        s.sendMessage("Done. " + parties.display(key) + " rep=" + parties.rep(key));
    }

    private boolean pchat(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Player only.");
            return true;
        }
        if (a.length == 0) {
            p.sendMessage(parties.prefix() + Util.color(" &c/pchat <pesan>"));
            return true;
        }
        parties.partyChat(p, String.join(" ", a));
        return true;
    }

    private boolean partyWar(CommandSender s, String[] a) {
        if (a.length == 0) {
            if (s instanceof Player p) plugin.war().showStatus(p);
            else s.sendMessage("/partywar status");
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("status")) {
            if (s instanceof Player p) plugin.war().showStatus(p);
            return true;
        }
        if (sub.equals("top")) {
            if (s instanceof Player p) plugin.war().showTop(p);
            return true;
        }
        if (sub.equals("hunt") || sub.equals("tracker") || sub.equals("compass")) {
            if (s instanceof Player p) plugin.war().hunt(p);
            return true;
        }
        if (!s.hasPermission("menkiestesparty.admin")) {
            s.sendMessage(Util.color("&cNo permission."));
            return true;
        }
        switch (sub) {
            case "start", "run" -> {
                int dur = a.length > 1 ? Util.parseInt(a[1], plugin.getConfig().getInt("war.default-duration-minutes", 30), 1, 1440) : plugin.getConfig().getInt("war.default-duration-minutes", 30);
                int target = a.length > 2 ? Util.parseInt(a[2], plugin.getConfig().getInt("war.default-target-points", 30), 1, 100000) : plugin.getConfig().getInt("war.default-target-points", 30);
                int prep = a.length > 3 ? Util.parseInt(a[3], plugin.getConfig().getInt("war.prepare-minutes", 10), 0, 120) : plugin.getConfig().getInt("war.prepare-minutes", 10);
                if (plugin.war().phase() != WarManager.Phase.NONE) s.sendMessage(Util.color("&cWar sudah aktif/prepare."));
                else plugin.war().start(dur, target, prep);
            }
            case "finish" -> plugin.war().finishManual();
            case "cancel", "stop" -> plugin.war().cancel("admin");
            default -> s.sendMessage(Util.color("&c/partywar start [durasi] [target] [prepare] | finish | cancel | status | top | hunt"));
        }
        return true;
    }

    private boolean partySeason(CommandSender s, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("status")) {
            s.sendMessage(Util.color("&dSeason: &f" + plugin.season().name() + " &8| &7Active: &f" + plugin.season().active()));
            return true;
        }
        if (a[0].equalsIgnoreCase("top")) {
            if (s instanceof Player p) plugin.season().showTop(p);
            return true;
        }
        if (!s.hasPermission("menkiestesparty.admin")) {
            s.sendMessage(Util.color("&cNo permission."));
            return true;
        }
        if (a[0].equalsIgnoreCase("start")) plugin.season().start(a.length > 1 ? a[1] : "Season_" + System.currentTimeMillis());
        else if (a[0].equalsIgnoreCase("end")) plugin.season().end();
        return true;
    }

    private boolean partyHall(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Player only.");
            return true;
        }
        handleHallPlayer(p, a);
        return true;
    }

    private void handleHallPlayer(Player p, String[] a) {
        if (a.length == 0) {
            plugin.hall().show(p);
            return;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "claim" -> plugin.hall().claim(p);
            case "item", "reward" -> plugin.hall().showConfiguredItem(p);
            case "setitem" -> plugin.hall().setRewardItem(p);
            case "clearitem" -> plugin.hall().clearRewardItem(p);
            default -> p.sendMessage(parties.prefix() + Util.color(" &7/partyhall [claim|item] &8| &cAdmin: setitem|clearitem"));
        }
    }

    private void showMembers(Player p) {
        String party = parties.partyOf(p.getUniqueId());
        if (party == null) {
            p.sendMessage(parties.prefix() + Util.color(" &cKamu belum punya Party."));
            return;
        }
        p.sendMessage(Util.color("&b&lMEMBERS &7- &f" + parties.display(party)));
        for (var u : parties.members(party)) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(u);
            p.sendMessage(Util.color("&7- &f" + (op.getName() == null ? u.toString().substring(0, 8) : op.getName()) + " &8[&b" + parties.role(u) + "&8]"));
        }
    }

    private void showRep(Player p) {
        String party = parties.partyOf(p.getUniqueId());
        if (party == null) {
            p.sendMessage(parties.prefix() + Util.color(" &cKamu belum punya Party."));
            return;
        }
        p.sendMessage(parties.prefix() + Util.color(" &7Reputation &b" + parties.display(party) + "&7: &f" + parties.rep(party)));
    }

    private void showLevel(Player p) {
        String party = parties.partyOf(p.getUniqueId());
        if (party == null) {
            p.sendMessage(parties.prefix() + Util.color(" &cKamu belum punya Party."));
            return;
        }
        p.sendMessage(parties.prefix() + Util.color(" &7Level: &b" + parties.level(party) + " &8| &7Slot: &f" + parties.memberCount(party) + "/" + parties.memberLimit(party) + " &8| &7Next Rep: &f" + (parties.nextLevelRep(party) < 0 ? "MAX" : parties.nextLevelRep(party))));
    }

    private void showQuest(Player p) {
        String party = parties.partyOf(p.getUniqueId());
        if (party == null) {
            p.sendMessage(parties.prefix() + Util.color(" &cKamu belum punya Party."));
            return;
        }
        p.sendMessage(Util.color("&e&lWEEKLY PARTY QUEST"));
        for (String q : List.of("mining", "hunter", "farmer")) p.sendMessage(Util.color("&7- " + parties.questLine(party, q)));
    }

    private void showRelic(Player p) {
        String party = parties.partyOf(p.getUniqueId());
        if (party == null) {
            p.sendMessage(parties.prefix() + Util.color(" &cKamu belum punya Party."));
            return;
        }
        p.sendMessage(parties.prefix() + Util.color(" &dParty Relic Lv." + parties.relicLevel(party) + " &8| &7Total misi: &f" + parties.relicMissions(party) + " &8| &7Target: &f3 / 9 / 18 / 30"));
    }

    private void showTop(Player p) {
        p.sendMessage(Util.color("&b&lPARTY TOP"));
        int i = 1;
        for (String key : parties.rankedParties("reputation", 10)) {
            p.sendMessage(Util.color("&e#" + i++ + " &b" + parties.display(key) + " &7- &f" + parties.rep(key) + " Rep"));
        }
    }

    private void help(CommandSender s) {
        s.sendMessage(Util.color("&8&m-----------------------------"));
        s.sendMessage(Util.color("&b&lMENKIESTES PARTY PLUGIN"));
        for (String line : List.of(
                "&f/party &7- GUI",
                "&f/party create <nama>",
                "&f/party invite <player>",
                "&f/party accept",
                "&f/party leave",
                "&f/party kick/promote/demote <player>",
                "&f/party sethome | home",
                "&f/party members | contribution",
                "&f/party rep | level | top",
                "&f/party quest | relic",
                "&f/party war [hunt/top]",
                "&f/party claimchest",
                "&f/party hall [claim|item]",
                "&cAdmin Hall: &f/partyhall setitem | clearitem",
                "&f/pchat <pesan>")) {
            s.sendMessage(Util.color(line));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = switch (command.getName().toLowerCase(Locale.ROOT)) {
                case "partywar" -> List.of("status", "top", "hunt", "start", "finish", "cancel");
                case "partyseason" -> List.of("status", "top", "start", "end");
                case "partyhall" -> List.of("claim", "item", "setitem", "clearitem");
                default -> List.of("create", "invite", "accept", "leave", "disband", "kick", "promote", "demote", "sethome", "home", "members", "contribution", "rep", "level", "top", "quest", "relic", "war", "claimchest", "hall", "help");
            };
            String q = args[0].toLowerCase(Locale.ROOT);
            return base.stream().filter(x -> x.startsWith(q)).toList();
        }
        return new ArrayList<>();
    }
}
