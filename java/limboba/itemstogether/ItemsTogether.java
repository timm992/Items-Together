package limboba.itemstogether;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ItemsTogether extends JavaPlugin implements Listener, TabCompleter {

    private double dropRadius = 1.5;
    private String defaultLanguage;

    private final Map<String, FileConfiguration> langFiles = new HashMap<>();
    private final List<String> supportedLanguages = Arrays.asList("en", "ru");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadLanguages();

        defaultLanguage = getConfig().getString("default-language", "en");
        if (!supportedLanguages.contains(defaultLanguage)) {
            defaultLanguage = "en";
            getLogger().warning("Unsupported default language in config.yml! Falling back to 'en'.");
        }

        reloadConfigValues();
        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("itemstogether") != null) {
            getCommand("itemstogether").setPermission("itemstogether.reload");
            getCommand("itemstogether").setTabCompleter(this);
        }

        getLogger().info("Plugin enabled! (1.16.5 – 1.21.1)");
    }

    private void loadLanguages() {
        File langDir = new File(getDataFolder(), "lang");
        if (!langDir.exists() && !langDir.mkdirs()) {
            getLogger().severe("Не удалось создать папку lang!");
            return;
        }

        for (String lang : supportedLanguages) {
            File langFile = new File(langDir, "messages_" + lang + ".yml");
            if (!langFile.exists()) {
                saveResource("lang/messages_" + lang + ".yml", false);
            }
            langFiles.put(lang, YamlConfiguration.loadConfiguration(langFile));
        }
    }

    private void reloadConfigValues() {
        reloadConfig();
        FileConfiguration cfg = getConfig();

        dropRadius = Math.max(cfg.getDouble("drop-radius", 1.5), 0);

        String lang = cfg.getString("default-language", "en");
        defaultLanguage = supportedLanguages.contains(lang) ? lang : "en";

        reloadLanguageFiles();
    }

    private void reloadLanguageFiles() {
        File langDir = new File(getDataFolder(), "lang");
        if (!langDir.exists()) return;

        for (String lang : supportedLanguages) {
            File langFile = new File(langDir, "messages_" + lang + ".yml");
            if (langFile.exists()) {
                langFiles.put(lang, YamlConfiguration.loadConfiguration(langFile));
            } else {
                getLogger().warning("Языковой файл не найден: messages_" + lang + ".yml");
            }
        }
    }

    private String msg(String key) {
        FileConfiguration messages = langFiles.get(defaultLanguage);
        String msg = messages != null ? messages.getString(key) : null;
        return translate(msg != null ? msg : "&c[Missing message: " + key + "]");
    }

    private String translate(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        List<ItemStack> drops = event.getDrops();
        if (drops == null || drops.isEmpty()) return;

        Location deathLocation = event.getEntity().getLocation();
        World world = deathLocation.getWorld();
        if (world == null) return;

        if (!world.isChunkLoaded(deathLocation.getBlockX() >> 4, deathLocation.getBlockZ() >> 4)) {
            return;
        }

        Collection<ItemStack> merged = mergeSimilarItems(drops);

        double baseX = deathLocation.getX() + 0.5;
        double baseY = deathLocation.getY() + 0.1;
        double baseZ = deathLocation.getZ() + 0.5;

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double radius = this.dropRadius;

        for (ItemStack stack : merged) {
            if (stack == null || stack.getAmount() <= 0) continue;

            double x = baseX;
            double z = baseZ;

            if (radius > 0.001) {
                x += (random.nextDouble() * 2.0 - 1.0) * radius;
                z += (random.nextDouble() * 2.0 - 1.0) * radius;
            }

            Location dropLoc = new Location(world, x, baseY, z);
            Item dropped = world.dropItem(dropLoc, stack);
            if (dropped != null) {
                dropped.setVelocity(new Vector(0, 0, 0));
                dropped.setPickupDelay(20);
            }
        }

        drops.clear();
    }

    private Collection<ItemStack> mergeSimilarItems(List<ItemStack> items) {
        List<ItemStack> result = new ArrayList<>(items.size());

        for (ItemStack input : items) {
            if (input == null || input.getAmount() <= 0) continue;

            boolean merged = false;
            int needed = input.getAmount();

            for (ItemStack output : result) {
                if (needed <= 0) break;
                if (output.isSimilar(input)) {
                    int max = output.getMaxStackSize();
                    int space = max - output.getAmount();
                    if (space > 0) {
                        int take = Math.min(space, needed);
                        output.setAmount(output.getAmount() + take);
                        needed -= take;
                        merged = true;
                    }
                }
            }

            while (needed > 0) {
                ItemStack clone = input.clone();
                int amount = Math.min(needed, clone.getMaxStackSize());
                clone.setAmount(amount);
                result.add(clone);
                needed -= amount;
            }
        }

        return result;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!"itemstogether".equalsIgnoreCase(command.getName())) return false;

        if (args.length == 1 && "reload".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission("itemstogether.reload") &&
                    !(sender instanceof Player && ((Player) sender).isOp()) &&
                    !(sender instanceof org.bukkit.command.ConsoleCommandSender)) {
                sender.sendMessage(msg("prefix") + msg("no-permission"));
                return true;
            }

            reloadConfigValues();
            sender.sendMessage(msg("prefix") + msg("reloaded"));
            return true;
        }

        sender.sendMessage(msg("prefix") + msg("available-command"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? Collections.singletonList("reload") : Collections.emptyList();
    }
}