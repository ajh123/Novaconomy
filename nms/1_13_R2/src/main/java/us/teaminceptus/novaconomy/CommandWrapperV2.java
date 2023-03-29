package us.teaminceptus.novaconomy;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.Plugin;
import revxrsal.commands.annotation.*;
import revxrsal.commands.autocomplete.SuggestionProvider;
import revxrsal.commands.bukkit.BukkitCommandActor;
import revxrsal.commands.bukkit.BukkitCommandHandler;
import revxrsal.commands.bukkit.annotation.CommandPermission;
import revxrsal.commands.exception.CommandErrorException;
import us.teaminceptus.novaconomy.abstraction.CommandWrapper;
import us.teaminceptus.novaconomy.abstraction.Wrapper;
import us.teaminceptus.novaconomy.api.Language;
import us.teaminceptus.novaconomy.api.NovaConfig;
import us.teaminceptus.novaconomy.api.business.Business;
import us.teaminceptus.novaconomy.api.corporation.Corporation;
import us.teaminceptus.novaconomy.api.corporation.CorporationInvite;
import us.teaminceptus.novaconomy.api.economy.Economy;
import us.teaminceptus.novaconomy.util.NovaSound;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static us.teaminceptus.novaconomy.abstraction.Wrapper.*;
import static us.teaminceptus.novaconomy.util.NovaUtil.format;

public final class CommandWrapperV2 implements CommandWrapper {

    private static BukkitCommandHandler handler;

    public CommandWrapperV2(Plugin plugin) {
        if (handler != null) return;

        handler = BukkitCommandHandler.create(plugin);

        handler.registerParameterValidator(String.class, (value, param, actor) -> {
            if (param.hasAnnotation(Length.class)) {
                int length = param.getAnnotation(Length.class).value();
                if (value.length() > length)
                    throw new CommandErrorException(format(getError("error.argument.length"), length));
            }
        });

        handler.registerValueResolver(Economy.class, ctx -> {
                Economy econ = Economy.getEconomy(ctx.popForParameter());
                if (econ == null) throw new CommandErrorException(getMessage("error.argument.economy"));
                return econ;
            }).registerValueResolver(Business.class, ctx -> {
                Business b = Business.byName(ctx.popForParameter());
                if (b == null) throw new CommandErrorException(getMessage("error.argument.business"));
                return b;
            }).registerValueResolver(Material.class, ctx -> {
                Material m = Material.matchMaterial(ctx.popForParameter());
                if (m == null) throw new CommandErrorException(getMessage("error.argument.icon"));
                if (!m.isItem()) throw new CommandErrorException(getMessage("error.argument.icon"));
                if (m == Material.AIR) throw new CommandErrorException(getMessage("error.argument.icon"));
                return m;
            }).registerValueResolver(OfflinePlayer.class, ctx -> {
                String value = ctx.popForParameter();
                if (value.equalsIgnoreCase("me")) return ((BukkitCommandActor) ctx.actor()).requirePlayer();
                OfflinePlayer p = Wrapper.getPlayer(value);
                if (p == null) throw new CommandErrorException(getMessage("error.argument.player"));
                return p;
            }).registerValueResolver(Corporation.class, ctx -> {
                Corporation c = Corporation.byName(ctx.popForParameter());
                if (c == null) throw new CommandErrorException(getError("error.argument.corporation"));
                return c;
            });

        handler.getAutoCompleter()
                .registerParameterSuggestions(Economy.class, SuggestionProvider.map(Economy::getEconomies, Economy::getName))
                .registerParameterSuggestions(Material.class, SuggestionProvider.map(() -> Arrays.stream(Material.values())
                        .filter(Material::isItem)
                        .filter(m -> m != Material.AIR)
                        .collect(Collectors.toList()), m -> m.name().toLowerCase())
                )
                .registerParameterSuggestions(Business.class, SuggestionProvider.map(Business::getBusinesses, Business::getName))
                .registerParameterSuggestions(boolean.class, SuggestionProvider.of("true", "false"))
                .registerParameterSuggestions(OfflinePlayer.class, SuggestionProvider.map(() -> Arrays.asList(Bukkit.getOfflinePlayers()), OfflinePlayer::getName))
                .registerParameterSuggestions(Corporation.class, SuggestionProvider.map(Corporation::getCorporations, Corporation::getName))

                // Suggestions

                .registerSuggestion("event", SuggestionProvider.map(NovaConfig.getConfiguration()::getAllCustomEvents, NovaConfig.CustomTaxEvent::getIdentifier))
                .registerSuggestion("settings", BUSINESS_TAG, "personal", CORPORATION_TAG)

                .registerSuggestion("blacklist", (args, sender, cmd) -> Business.getBusinesses().stream().filter(b -> {
                    OfflinePlayer p = ((BukkitCommandActor) sender).requirePlayer();
                    return !b.isOwner(p) && !Business.byOwner(p).isBlacklisted(b);
                }).map(Business::getName).collect(Collectors.toList()))

                .registerSuggestion("blacklisted", (args, sender, cmd) -> Business.byOwner(Wrapper.getPlayer(sender.getName())).getBlacklist().stream().map(Business::getName).collect(Collectors.toList()))
                .registerSuggestion("natural_causes",
                "enchant_bonus", "max_increase", "kill_increase", "kill_increase_chance", "kill_increase_indirect", "fishing_increase",
                "fishing_increase_chance", "mining_increase", "mining_increase_chance", "farming_increase", "farming_increase_chance",
                "death_decrease", "death_divider")

                .registerSuggestion("modifiers", "killing", "mining", "farming", "fishing", "death")
                .registerSuggestion("modifier_keys", (args, sender, cmd) -> {
                    String type = args.get(4);

                    switch (type.toLowerCase()) {
                        case "mining": return Arrays.stream(Material.values())
                                .filter(Material::isBlock)
                                .filter(m -> m != Material.AIR)
                                .map(m -> m.name().toLowerCase())
                                .collect(Collectors.toList());
                        case "farming": return Arrays.stream(Material.values())
                                .filter(w::isCrop)
                                .map(m -> m.name().toLowerCase())
                                .collect(Collectors.toList());
                        case "fishing": {
                            List<String> values = new ArrayList<>();
                            values.addAll(Arrays.stream(Material.values())
                                    .filter(Material::isItem)
                                    .map(m -> m.name().toLowerCase())
                                    .collect(Collectors.toList()));
                            values.addAll(Arrays.stream(EntityType.values())
                                    .filter(EntityType::isAlive)
                                    .filter(e -> LivingEntity.class.isAssignableFrom(e.getEntityClass()))
                                    .map(e -> e.name().toLowerCase())
                                    .collect(Collectors.toList()));

                            return values;
                        }
                        case "killing": return Arrays.stream(EntityType.values())
                                .filter(EntityType::isAlive)
                                .filter(e -> LivingEntity.class.isAssignableFrom(e.getEntityClass()))
                                .map(e -> e.name().toLowerCase())
                                .collect(Collectors.toList());
                        case "death": return Arrays.stream(EntityDamageEvent.DamageCause.values())
                                .map(d -> d.name().toLowerCase())
                                .collect(Collectors.toList());
                        default: return new ArrayList<>();
                    }
                })
                .registerSuggestion("modifier_keys_existing", (args, sender, cmd) -> {
                    String type = args.get(4);
                    FileConfiguration config = NovaConfig.getConfig();

                    switch (type.toLowerCase()) {
                        case "mining": return config.getConfigurationSection("NaturalCauses.Modifiers.Mining")
                                .getKeys(false)
                                .stream()
                                .map(String::toLowerCase)
                                .collect(Collectors.toSet());
                        case "farming": return config.getConfigurationSection("NaturalCauses.Modifiers.Farming")
                                .getKeys(false)
                                .stream()
                                .map(String::toLowerCase)
                                .collect(Collectors.toSet());
                        case "fishing": return config.getConfigurationSection("NaturalCauses.Modifiers.Fishing")
                                .getKeys(false)
                                .stream()
                                .map(String::toLowerCase)
                                .collect(Collectors.toSet());
                        case "killing": return config.getConfigurationSection("NaturalCauses.Modifiers.Killing")
                                .getKeys(false)
                                .stream()
                                .map(String::toLowerCase)
                                .collect(Collectors.toSet());
                        case "death": return config.getConfigurationSection("NaturalCauses.Modifiers.Death")
                                .getKeys(false)
                                .stream()
                                .map(String::toLowerCase)
                                .collect(Collectors.toSet());
                        default: return new ArrayList<>();
                    }
                })
                .registerSuggestion("invites", (args, sender, cmd) -> {
                    Business b = Business.byOwner(((BukkitCommandActor) sender).requirePlayer());
                    return b.getInvites()
                            .stream()
                            .map(CorporationInvite::getFrom)
                            .map(Corporation::getName)
                            .collect(Collectors.toList());
                });

        handler.register(this);
        new EconomyCommands(this);
        new BusinessCommands(this);
        new BankCommands(this);
        new BountyCommands(this);
        new NovaConfigCommands(this);
        new CorporationCommands(this);

        handler.registerBrigadier();
        handler.setLocale(Language.getCurrentLocale());

        plugin.getLogger().info("Loaded Command Version v2 (1.13.2+)");
    }

    // Lamp Impl

    private boolean economyCount(Player p) {
        if (Economy.getEconomies().isEmpty()) {
            p.sendMessage(getMessage("error.economy.none"));
            return false;
        }

        return true;
    }

    @Override
    @Command({"ehelp", "nhelp", "novahelp", "econhelp", "economyhelp"})
    @Description("Economy help")
    @Usage("/ehelp")
    public void help(CommandSender sender) {
        CommandWrapper.super.help(sender);
    }

    @Override
    @Command({"balance", "bal", "novabal"})
    @Description("Access your balances from all economies")
    @Usage("/balance")
    @CommandPermission("novaconomy.user.balance")
    public void balance(Player p) {
        if (economyCount(p)) CommandWrapper.super.balance(p);
    }

    @Override
    @Command({"pay", "econpay", "novapay", "givemoney", "givebal"})
    @Description("Pay another user")
    @CommandPermission("novaconomy.user.pay")
    public void pay(Player p, Player target, @Optional Economy economy, @Default("0") @Range(min = 0) double amount) { CommandWrapper.super.pay(p, target, economy, amount); }

    @Override
    @Command({"convert", "conv"})
    @Description("Convert one balance in an economy to another balance")
    @Usage("/convert <econ-from> <econ-to> <amount>")
    @CommandPermission("novaconomy.user.convert")
    public void convert(Player p, @Named("from-economy") Economy from, @Named("to-economy") Economy to, @Range(min = 0.01) double amount) { CommandWrapper.super.convert(p, from, to, amount); }

    @Override
    @Command({"novaconomyreload", "novareload", "nreload", "econreload"})
    @Usage("/novareload")
    @Description("Reload Novaconomy Configuration")
    @CommandPermission("novaconomy.admin.config")
    public void reloadConfig(CommandSender sender) { CommandWrapper.super.reloadConfig(sender); }

    @Command({"createcheck", "check", "ncheck", "nc", "novacheck"})
    @Usage("/createcheck <economy> <amount>")
    @Description("Create a Novaconomy Check redeemable for a certain amount of money")
    @CommandPermission("novaconomy.user.check")
    public void createCheck(Player p, @Range(min = 1) double amount, Economy econ) { CommandWrapper.super.createCheck(p, econ, amount, true); }

    @Override
    @Command({"exchange", "convertgui", "convgui", "exch"})
    @Usage("/exchange <amount>")
    @Description("Convert one balance in an economy to another balance (with a GUI)")
    @CommandPermission("novaconomy.user.convert")
    public void exchange(Player p, @Range(min = 0.01) double amount) { CommandWrapper.super.exchange(p, amount); }

    @Override
    @Command({"balanceleaderboard", "bleaderboard", "nleaderboard", "bl", "nl", "novaleaderboard", "balboard", "novaboard"})
    @Usage("/balanceleaderboard [<economy>]")
    @Description("View the top 15 balances of all or certain economies")
    @CommandPermission("novaconomy.user.leaderboard")
    public void balanceLeaderboard(Player p, @Optional Economy econ) { CommandWrapper.super.balanceLeaderboard(p, econ); }

    @Override
    @Command({"settings", "novasettings", "nsettings"})
    @Usage("/settings [<business|personal>]")
    @Description("Manage your Novaconomy Settings")
    @AutoComplete("@settings")
    public void settings(Player p, @Optional String section) { CommandWrapper.super.settings(p, section); }

    @Override
    @Command({"taxevent", "customtax"})
    @Usage("/taxevent <event> [<self>]")
    @Description("Call a Custom Tax Event from the configuration")
    @CommandPermission("novaconomy.admin.tax_event")
    @AutoComplete("@event *")
    public void callEvent(CommandSender sender, String event, @Default("true") boolean self) { CommandWrapper.super.callEvent(sender, event, self); }

    @Override
    @Command({"rate", "novarate", "nrate", "ratebusiness"})
    @Usage("/rate <business> [<comment>]")
    @Description("Rate a business")
    @CommandPermission("novaconomy.user.rate")
    public void rate(Player p, Business business, @Default("") String comment) { CommandWrapper.super.rate(p, business, comment); }

    @Override
    @Command({"statistics", "stats", "pstats", "pstatistics", "playerstats", "playerstatistics", "nstats", "nstatistics"})
    @Usage("/statistics")
    @Description("View your Novaconomy Statistics")
    @CommandPermission("novaconomy.user.stats")
    public void playerStatistics(Player p, @Default("me") OfflinePlayer target) { CommandWrapper.super.playerStatistics(p, target); }

    @Command({"businessleaderboard", "bboard", "businessl", "businessboard"})
    @Usage("/businessleaderboard")
    @Description("View the top 10 businesses in various categories")
    @CommandPermission("novaconomy.user.leaderboard")
    public void businessLeaderboard(Player p) { CommandWrapper.super.businessLeaderboard(p, "ratings"); }

    @Command({"business", "nbusiness", "nb", "b"})
    @Description("Manage your Novaconomy Business")
    @Usage("/business <create|info|delete|addproduct|stock|query|...> <args...>")
    private static final class BusinessCommands {

        private final CommandWrapperV2 wrapper;

        private BusinessCommands(CommandWrapperV2 wrapper) {
            this.wrapper = wrapper;

            handler.register(this);
        }

        @Default
        public void businessInfoDefault(Player p) { businessInfo(p); }

        @Subcommand({"info", "information"})
        public void businessInfo(Player p) { wrapper.businessInfo(p); }

        @Subcommand("query")
        @CommandPermission("novaconomy.user.business.query")
        public void businessQuery(Player p, Business business) { wrapper.businessQuery(p, business); }

        @Subcommand("create")
        @CommandPermission("novaconomy.user.business.create")
        public void businessCreate(Player p, String name, Material icon) { wrapper.createBusiness(p, name, icon);}

        @Subcommand({"addproduct", "addp"})
        public void addProduct(Player p, @Range(min = 0.01) double price) { wrapper.addProduct(p, price); }

        @Subcommand({"addresource", "stock", "addr", "addstock"})
        @CommandPermission("novaconomy.user.business.resources")
        public void addResource(Player p) { wrapper.addResource(p); }

        @Subcommand({"removeproduct", "removep"})
        public void removeProduct(Player p) { wrapper.removeProduct(p); }

        @Subcommand("home")
        @CommandPermission("novaconomy.user.business.home")
        public void home(Player p) { wrapper.businessHome(p, false); }

        @Subcommand("sethome")
        @CommandPermission("novaconomy.user.business.home")
        public void setHome(Player p) { wrapper.businessHome(p, true); }

        @Subcommand({"setname", "name"})
        public void setName(Player p, @Single String name) { wrapper.setBusinessName(p, name); }

        @Subcommand({"seticon", "icon"})
        public void setIcon(Player p, Material icon) { wrapper.setBusinessIcon(p, icon); }

        @Subcommand("delete")
        public void deleteBusiness(Player p, @Default("") String confirm) { wrapper.deleteBusiness(p, confirm.equalsIgnoreCase("confirm")); }

        @Subcommand("recover")
        public void businessRecover(Player p) { wrapper.businessRecover(p); }

        @Subcommand({"statistics", "stats"})
        public void businessStatistics(Player p) { wrapper.businessStatistics(p, Business.byOwner(p));}

        @Subcommand({"settings", "setting"})
        public void settings(Player p) { wrapper.settings(p, "business"); }

        @Subcommand("rating")
        public void businessRating(Player p, OfflinePlayer target) { wrapper.businessRating(p, target); }

        @Subcommand("discover")
        public void discoverBusiness(Player p, @Default("") String keywords) { wrapper.discoverBusinesses(p, keywords.split("[ ,]")); }

        @Subcommand("remove")
        @CommandPermission("novaconomy.admin.delete_business")
        public void removeBusiness(CommandSender sender, Business b, @Default("") String confirm) { wrapper.removeBusiness(sender, b, confirm.equalsIgnoreCase("confirm"));}

        @Subcommand({"editprice", "price"})
        public void editPrice(Player p, @Range(min = 0.01) double newPrice, @Optional Economy economy) { wrapper.editPrice(p, newPrice, economy); }

        @Subcommand({"keyword", "keywords"})
        @Default
        @CommandPermission("novaconomy.user.business.keywords")
        public void keywords(Player p) { keywordsList(p); }

        @Subcommand({"keyword list", "keywords list", "keyword l", "keywords l"})
        @CommandPermission("novaconomy.user.business.keywords")
        public void keywordsList(Player p) { wrapper.listKeywords(p); }

        @Subcommand({"keywords add", "keyword add"})
        @CommandPermission("novaconomy.user.business.keywords")
        public void addKeywords(Player p, String keywords) {
            wrapper.addKeywords(p, keywords.split("[ ,]"));
        }

        @Subcommand({"keywords remove", "keyword remove", "keywords delete", "keyword delete"})
        @CommandPermission("novaconomy.user.business.keywords")
        public void removeKeywords(Player p, String keywords) {
            wrapper.removeKeywords(p, keywords.split("[ ,]"));
        }

        @Default
        @Subcommand({"advertising", "ads", "advertise"})
        public void businessAdvertising(Player p) {
            wrapper.businessAdvertising(p);
        }

        @Subcommand({"advertising addbalance", "ads addbalance", "advertise addbalance",
                "advertising addbal", "ads addbal", "advertise addbal",
                "advertising add", "ads add", "advertise add"})
        public void addAdvertising(Player p) {
            wrapper.businessAdvertisingChange(p, true);
        }

        @Subcommand({"advertising removebalance", "ads removebalance", "advertise removebalance",
                "advertising removebal", "ads removebal", "advertise removebal",
                "advertising remove", "ads remove", "advertise remove"})
        public void removeAdvertising(Player p) {
            wrapper.businessAdvertisingChange(p, false);
        }

        @Subcommand({"blacklist", "blist", "bl", "blackl",
                "blacklist list", "blist list", "bl list", "blackl list",
                "blacklist l", "blist l", "bl l", "blackl l"})
        @Default
        public void listBlacklist(Player p) { wrapper.listBlacklist(p); }

        @Subcommand({"blacklist add", "blist add", "bl add", "blackl add"})
        @AutoComplete("@blacklist *")
        public void blacklistBusiness(Player p, Business business) { wrapper.addBlacklist(p, business); }

        @Subcommand({"blacklist remove", "blist remove", "bl remove", "blackl remove",
        "blacklist delete", "blist delete", "bl delete", "blackl delete"})
        @AutoComplete("@blacklisted *")
        public void unblacklistBusiness(Player p, Business business) { wrapper.removeBlacklist(p, business); }

        @Subcommand({"allratings", "allrating", "allr", "ar"})
        public void allBusinessRatings(Player p) { wrapper.allBusinessRatings(p); }

        @Subcommand("invite accept")
        @AutoComplete("@invites *")
        public void acceptInvite(Player p, Corporation from) { wrapper.acceptCorporationInvite(p, from); }

        @Subcommand("invite decline")
        @AutoComplete("@invites *")
        public void declineInvite(Player p, Corporation from) { wrapper.declineCorporationInvite(p, from); }

        @Subcommand("join")
        @CommandPermission("novaconomy.user.business.join_corporation")
        public void joinCorporation(Player p, Corporation corp) { wrapper.joinCorporation(p, corp); }

        @Subcommand("leave")
        public void leaveCorporation(Player p) { wrapper.leaveCorporation(p); }
    }

    @Command({"nbank", "bank", "globalbank", "gbank"})
    @Description("Interact with the Global Novaconomy Bank")
    @Usage("/bank <info|deposit|withdraw|transfer|leaderboard|...> <args...>")
    private static final class BankCommands {
        private final CommandWrapperV2 wrapper;

        private BankCommands(CommandWrapperV2 wrapper) {
            this.wrapper = wrapper;

            handler.register(this);
        }

        @Subcommand({"info", "information", "balances", "balance"})
        @CommandPermission("novaconomy.user.bank.info")
        public void bankBalances(Player p) { wrapper.bankBalances(p); }

        @Subcommand("deposit")
        @CommandPermission("novaconomy.user.bank.deposit")
        public void bankDeposit(Player p, @Range(min = 0.01) double amount, Economy econ) { wrapper.bankDeposit(p, amount, econ);}

        @Subcommand("withdraw")
        @CommandPermission("novaconomy.user.bank.withdraw")
        public void bankWithdraw(Player p, @Range(min = 0.01) double amount, Economy econ) { wrapper.bankWithdraw(p, amount, econ);}
    }

    @Command({"economy", "econ", "novaecon", "novaconomy", "necon"})
    @Description("Manage economies or their balances")
    @Usage("/economy <create|delete|addbal|removebal|info> <args...>")
    private static final class EconomyCommands {

        private final CommandWrapperV2 wrapper;

        private EconomyCommands(CommandWrapperV2 wrapper) {
            this.wrapper = wrapper;

            handler.getAutoCompleter().registerSuggestion("symbol", SuggestionProvider.of("'$'", "'%'", "Q", "L", "P", "A", "a", "r", "R", "C", "c", "D", "d", "W", "w", "B", "b"));
            handler.getAutoCompleter().registerSuggestion("interest", SuggestionProvider.of("enable", "disable"));

            handler.register(this);
        }

        @Subcommand({"create", "make"})
        @AutoComplete("* @symbol *")
        @CommandPermission("novaconomy.economy.create")
        public void createEconomy(CommandSender sender, String name, String symbol, Material icon, @Default("1") @Range(min = 0.01, max = Integer.MAX_VALUE) double scale, @Named("natural-increase") @Default("true") boolean naturalIncrease, @Named("clickable-reward") @Default("true") boolean clickableReward) {
            wrapper.createEconomy(sender, name, symbol.startsWith("\"") || symbol.startsWith("'") ? symbol.charAt(1) : symbol.charAt(0), icon, scale, naturalIncrease, clickableReward);
        }

        @Subcommand({"remove", "delete", "removeeconomy", "deleteeconomy"})
        @CommandPermission("novaconomy.economy.delete")
        public void removeEconomy(CommandSender sender, Economy economy) {
            wrapper.removeEconomy(sender, economy);
        }

        @Subcommand("interest")
        @AutoComplete("@interest")
        @CommandPermission("novaconomy.economy.interest")
        public void interest(CommandSender sender, @Default("enable") String interest) {
            wrapper.interest(sender, interest.equalsIgnoreCase("enable") || interest.equalsIgnoreCase("true"));
        }

        @Subcommand({"setbalance", "setbal"})
        @CommandPermission("novaconomy.economy.setbalance")
        public void setBalance(CommandSender sender, Economy economy, Player target, @Range(min = 0) double amount) {
            wrapper.setBalance(sender, economy, target, amount);
        }

        @Subcommand({"addbalance", "addbal"})
        @CommandPermission("novaconomy.economy.addbalance")
        public void addBalance(CommandSender sender, Economy economy, Player target, @Range(min = 0) double amount) {
            wrapper.addBalance(sender, economy, target, amount);
        }

        @Subcommand({"removebalance", "removebal"})
        @CommandPermission("novaconomy.economy.removebalance")
        public void removeBalance(CommandSender sender, Economy economy, Player target, @Range(min = 0) double amount) {
            wrapper.removeBalance(sender, economy, target, amount);
        }

        @Subcommand({"setmodeldata", "setcustommodeldata", "modeldata", "custommodeldata"})
        @CommandPermission("novaconomy.economy.create")
        public void setCustomModelData(CommandSender sender, Economy economy, int modelData) {
            wrapper.setEconomyModel(sender, economy, modelData);
        }

        @Subcommand({"seticon", "icon"})
        @CommandPermission("novaconomy.economy.create")
        public void setIcon(CommandSender sender, Economy economy, Material icon) { wrapper.setEconomyIcon(sender, economy, icon); }

        @Subcommand({"setconversionscale", "conversionscale", "setscale", "scale"})
        @CommandPermission("novaconomy.economy.create")
        public void setConversionScale(CommandSender sender, Economy economy, @Range(min = 0.01) double scale) { wrapper.setEconomyScale(sender, economy, scale); }

        @Subcommand({"setnaturalincrease", "naturalincrease", "setnatural", "natural"})
        @CommandPermission("novaconomy.economy.create")
        public void setNaturalIncrease(CommandSender sender, Economy economy, @Default("true") boolean naturalIncrease) { wrapper.setEconomyNatural(sender, economy, naturalIncrease); }

        @Subcommand({"check", "createcheck"})
        @CommandPermission("novaconomy.economy.check")
        public void createCheck(Player p, Economy economy, @Range(min = 1) double amount) { wrapper.createCheck(p, economy, amount, false); }

        @Subcommand("info")
        @CommandPermission("novaconomy.economy.info")
        public void info(CommandSender sender, Economy economy) {
            wrapper.economyInfo(sender, economy);
        }

        @Subcommand({"setname", "name"})
        @CommandPermission("novaconomy.economy.create")
        public void setName(CommandSender sender, Economy economy, @Single String name) { wrapper.setEconomyName(sender, economy, name); }

        @Subcommand({"setclickablereward", "clickablereward", "setclickable", "clickable"})
        @CommandPermission("novaconomy.economy.create")
        public void setClickableReward(CommandSender sender, Economy economy, @Default("true") boolean clickableReward) { wrapper.setEconomyRewardable(sender, economy, clickableReward); }
    }

    @Command({"bounty", "novabounty", "nbounty"})
    @Description("Manage your Novaconomy Bounties")
    @Usage("/bounty <owned|create|delete|self> <args...>")
    private static final class BountyCommands {

        private final CommandWrapperV2 wrapper;

        BountyCommands(CommandWrapperV2 wrapper) {
            this.wrapper = wrapper;
            handler.register(this);
        }

        @Subcommand({"create", "add"})
        @CommandPermission("novaconomy.user.bounty.manage")
        public void createBounty(Player p, OfflinePlayer target, Economy economy, @Range(min = 0.01) double amount) { wrapper.createBounty(p, target, economy, amount); }

        @Subcommand({"delete", "remove"})
        @CommandPermission("novaconomy.user.bounty.manage")
        public void removeBounty(Player p, OfflinePlayer target) { wrapper.deleteBounty(p, target); }

        @Subcommand("owned")
        @CommandPermission("novaconomy.user.bounty.list")
        public void listOwnedBounties(Player p) { wrapper.listBounties(p, true); }

        @Subcommand("self")
        @CommandPermission("novaconomy.user.bounty.list")
        public void listSelfBounties(Player p) { wrapper.listBounties(p, false); }

    }

    @Command({"novaconfig", "novaconomyconfig", "nconfig", "nconf"})
    @Description("View or edit the Novaconomy Configuration")
    @Usage("/novaconfig <naturalcauses|reload|rl|...> <args...>")
    @CommandPermission("novaconomy.admin.config")
    private static final class NovaConfigCommands {

        private final CommandWrapperV2 wrapper;

        NovaConfigCommands(CommandWrapperV2 wrapper) {
            this.wrapper = wrapper;
            handler.register(this);
        }

        @Subcommand({"reload", "rl"})
        public void reload(CommandSender sender) {
            wrapper.reloadConfig(sender);
        }

        @Subcommand({"defaultecon", "setdefaulteconomy", "setdefaultecon", "defaulteconomy"})
        public void setDefaultEconomy(CommandSender sender, Economy econ) {
            wrapper.setDefaultEconomy(sender, econ);
        }

        // NaturalCauses Configuration

        @Subcommand({"naturalcauses view", "ncauses view", "naturalc view", "nc view"})
        @AutoComplete("@natural_causes *")
        public void viewNaturalCauses(CommandSender sender, String key) {
            wrapper.configNaturalCauses(sender, key, null);
        }

        @Subcommand({"naturalcauses set", "ncauses set", "naturalc set", "nc set"})
        @AutoComplete("@natural_causes *")
        public void setNaturalCauses(CommandSender sender, String key, String value) {
            wrapper.configNaturalCauses(sender, key, value);
        }

        @Subcommand({"naturalcauses modifier add", "naturalcauses mod add", "naturalcauses mod create", "naturalcauses modifier create",
        "ncauses modifier add", "ncauses mod add", "ncauses mod create", "ncauses modifier create",
        "naturalc modifier add", "naturalc mod add", "naturalc mod create", "naturalc modifier create",
        "nc modifier add", "nc mod add", "nc mod create", "nc modifier create"})
        @AutoComplete("@modifiers @modifier_keys")
        public void addCausesModifier(CommandSender sender, String type, String key, String values) {
            wrapper.addCausesModifier(sender, type, key, values.replace(" ", ",").split(","));
        }

        @Subcommand({"naturalcauses modifier remove", "naturalcauses mod remove", "naturalcauses modifier delete", "naturalcauses mod delete",
        "ncauses modifier remove", "ncauses mod remove", "ncauses modifier delete", "ncauses mod delete",
        "naturalc modifier remove", "naturalc mod remove", "naturalc modifier delete", "naturalc mod delete",
        "nc modifier remove", "nc mod remove", "nc modifier delete", "nc mod delete"})
        @AutoComplete("@modifiers @modifier_keys_existing")
        public void removeCausesModifier(CommandSender sender, String type, String key) {
            wrapper.removeCausesModifier(sender, type, key);
        }

        @Subcommand({"naturalcauses modifier view", "naturalcauses mod view",
        "ncauses modifier view", "ncauses mod view",
        "naturalc modifier view", "naturalc mod view",
        "nc modifier view", "nc mod view"})
        @AutoComplete("@modifiers @modifier_keys_existing")
        public void viewCausesModifier(CommandSender sender, String type, String key) {
            wrapper.viewCausesModifier(sender, type, key);
        }

        // Business Configuration

        @Subcommand({"business advertising enable", "businesses advertising enable", "bs advertising enable",
        "business ads enable", "businesses ads enable", "bs ads enable",
        "business advertising on", "businesses advertising on", "bs advertising on",
        "business ads on", "businesses ads on", "bs ads on"})
        public void enableBusinessAds(CommandSender sender) {
            wrapper.basicConfig(sender, "Business.Advertising.Enabled", true);
        }

        @Subcommand({"business advertising disable", "businesses advertising disable", "bs advertising disable",
        "business ads disable", "businesses ads disable", "bs ads disable",
        "business advertising off", "businesses advertising off", "bs advertising off",
        "business ads off", "businesses ads off", "bs ads off"})
        public void disableBusinessAds(CommandSender sender) {
            wrapper.basicConfig(sender, "Business.Advertising.Enabled", false);
        }

        @Subcommand({"business advertising clickreward", "businesses advertising clickreward", "bs advertising clickreward",
        "business ads clickreward", "businesses ads clickreward", "bs ads clickreward"})
        public void setBusinessAdsClickReward(CommandSender sender, @Range(min = 0) double reward) {
            wrapper.basicConfig(sender, "Business.Advertising.ClickReward", reward);
        }

        // Bounties Configuration

        @Subcommand({"bounties enable", "bounty enable", "bounties on", "bounty on"})
        public void enableBounties(CommandSender sender) {
            wrapper.basicConfig(sender, "Bounties.Enabled", true);
        }

        @Subcommand({"bounties disable", "bounty disable", "bounties off", "bounty off"})
        public void disableBounties(CommandSender sender) {
            wrapper.basicConfig(sender, "Bounties.Enabled", false);
        }

        @Subcommand({"bounties broadcast", "bounty broadcast"})
        public void setBountyBroadcast(CommandSender sender, boolean broadcast) {
            wrapper.basicConfig(sender, "Bounties.Broadcast", broadcast);
        }
    }

    @Command({"corporation", "corp", "ncorp", "c", "nc"})
    @Description("Manage your Novaconomy Corporation")
    @Usage("/corporation <create|delete|info|...> <args...>")
    private static final class CorporationCommands {

        private final CommandWrapperV2 wrapper;

        CorporationCommands(CommandWrapperV2 wrapper) {
            this.wrapper = wrapper;
            handler.register(this);
        }

        @Default
        public void defaultCorporationInfo(Player p) { corporationInfo(p); }

        @Subcommand("info")
        public void corporationInfo(Player p) {
            wrapper.corporationInfo(p);
            NovaSound.ENTITY_ARROW_HIT_PLAYER.playSuccess(p);
        }

        @Subcommand("create")
        @CommandPermission("novaconomy.user.corporation.manage")
        public void createCorporation(Player p, String name, Material icon) {
            wrapper.createCorporation(p, name, icon);
        }

        @Subcommand("delete")
        public void deleteCorporation(Player p, @Single String confirm) {
            wrapper.deleteCorporation(p, confirm.equalsIgnoreCase("confirm"));
        }

        @Subcommand({"setdescription", "setdesc"})
        @CommandPermission("novaconomy.user.corporation.manage")
        public void setDescription(Player p, String description) {
            wrapper.setCorporationDescription(p, description);
        }

        @Subcommand({"seticon", "icon"})
        @CommandPermission("novaconomy.user.corporation.manage")
        public void setIcon(Player p, Material icon) {
            wrapper.setCorporationIcon(p, icon);
        }

        @Subcommand({"setheadquarters", "sethq"})
        public void setHeadquarters(Player p) {
            wrapper.setCorporationHeadquarters(p);
        }

        @Subcommand({"setname", "name"})
        public void setName(Player p, @Single String name) {
            wrapper.setCorporationName(p, name);
        }

        @Subcommand("achievements")
        public void corporationAchievements(Player p) {
            wrapper.corporationAchievements(p);
        }

        @Subcommand({"leveling", "levelinfo", "level", "progress", "prog"})
        public void corporationLeveling(Player p) {
            wrapper.corporationLeveling(p);
        }

        @Subcommand({"stats", "statistics"})
        public void corporationStatistics(Player p) {
            wrapper.corporationStatistics(p);
        }

        @Subcommand("invite")
        public void inviteBusiness(Player p, Business b) {
            wrapper.inviteBusiness(p, b);
        }

        @Subcommand({"setexperience", "experience", "setexp", "exp"})
        @CommandPermission("novaconomy.admin.corporation.manage_experience")
        public void setCorporationExperience(CommandSender sender, Corporation c, @Range(min = 0) double exp) {
            wrapper.setCorporationExperience(sender, c, exp);
        }

        @Subcommand({"addexperience", "addexp"})
        @CommandPermission("novaconomy.admin.corporation.manage_experience")
        public void addCorporationExperience(CommandSender sender, Corporation c, @Range(min = 0) double exp) {
            wrapper.setCorporationExperience(sender, c, c.getExperience() + exp);
        }

        @Subcommand({"removeexperience", "removeexp"})
        @CommandPermission("novaconomy.admin.corporation.manage_experience")
        public void removeCorporationExperience(CommandSender sender, Corporation c, @Range(min = 0) double exp) {
            wrapper.setCorporationExperience(sender, c, c.getExperience() - exp);
        }

        @Subcommand("setlevel")
        @CommandPermission("novaconomy.admin.corporation.manage_experience")
        public void setCorporationLevel(CommandSender sender, Corporation c, @Range(min = 0, max = Corporation.MAX_LEVEL) int level) {
            wrapper.setCorporationExperience(sender, c, Corporation.toExperience(level));
        }

        @Subcommand({"hq", "headquarters"})
        public void corporationHeadquarters(Player p) { wrapper.corporationHeadquarters(p); }
    }
}