package su.nightexpress.dungeons;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.dungeons.ComponentUtilities.SpecialButtonListener;
import su.nightexpress.dungeons.api.dungeon.DungeonEntityBridge;
import su.nightexpress.dungeons.command.impl.BaseCommands;
import su.nightexpress.dungeons.command.impl.KitCommands;
import su.nightexpress.dungeons.command.impl.SetupCommands;
import su.nightexpress.dungeons.config.Config;
import su.nightexpress.dungeons.config.Keys;
import su.nightexpress.dungeons.config.Lang;
import su.nightexpress.dungeons.config.Perms;
import su.nightexpress.dungeons.data.DataHandler;
import su.nightexpress.dungeons.dungeon.DungeonManager;
import su.nightexpress.dungeons.dungeon.DungeonSetup;
import su.nightexpress.dungeons.dungeon.Party.PartyManager;
import su.nightexpress.dungeons.dungeon.SimilarDungeonManager;
import su.nightexpress.dungeons.dungeon.classes.ClassManager;
import su.nightexpress.dungeons.dungeon.criteria.registry.CriteriaRegistry;
import su.nightexpress.dungeons.dungeon.reward.FinishChestListener;
import su.nightexpress.dungeons.dungeon.listener.OrbListener;
import su.nightexpress.dungeons.dungeon.player.SoloManager;
import su.nightexpress.dungeons.dungeon.reward.FinishChestRewardManager;
import su.nightexpress.dungeons.dungeon.reward.RewardManager;
import su.nightexpress.dungeons.dungeon.scale.ScaleBaseRegistry;
import su.nightexpress.dungeons.dungeon.script.action.ActionRegistry;
import su.nightexpress.dungeons.dungeon.script.condition.ConditionRegistry;
import su.nightexpress.dungeons.dungeon.script.number.NumberComparators;
import su.nightexpress.dungeons.dungeon.script.task.TaskRegistry;
import su.nightexpress.dungeons.gui.GuiListener.*;
import su.nightexpress.dungeons.gui.Utils.GUIConfigManager;
import su.nightexpress.dungeons.hook.HookId;
import su.nightexpress.dungeons.hook.impl.McMMOHook;
import su.nightexpress.dungeons.hook.impl.PlaceholderHook;
import su.nightexpress.dungeons.kit.KitManager;
import su.nightexpress.dungeons.mob.MobManager;
import su.nightexpress.dungeons.mob.variant.MobVariantRegistry;
import su.nightexpress.dungeons.nms.DungeonNMS;
import su.nightexpress.dungeons.nms.paper.PaperDungeonNMS;
import su.nightexpress.dungeons.registry.compat.BoardPluginRegistry;
import su.nightexpress.dungeons.registry.compat.GodPluginRegistry;
import su.nightexpress.dungeons.registry.level.LevelRegistry;
import su.nightexpress.dungeons.registry.mob.MobRegistry;
import su.nightexpress.dungeons.registry.pet.PetRegistry;
import su.nightexpress.dungeons.selection.SelectionManager;
import su.nightexpress.dungeons.user.UserManager;
import su.nightexpress.nightcore.NightPlugin;
import su.nightexpress.nightcore.commands.command.NightCommand;
import su.nightexpress.nightcore.config.PluginDetails;
import su.nightexpress.nightcore.util.Plugins;
import su.nightexpress.dungeons.kit.OrbManager;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DungeonPlugin extends NightPlugin {

    private static final Pattern MINECRAFT_VERSION_PATTERN = Pattern.compile("\\(MC: ([^)]+)\\)");

    private DataHandler dataHandler;
    private UserManager userManager;

    private SelectionManager selectionManager;
    private MobManager       mobManager;
    private KitManager       kitManager;
    private DungeonManager   dungeonManager;
    private DungeonSetup dungeonSetup;

    private DungeonNMS internals;

    private PartyManager partyManager;
    private SoloManager soloManager;
    private GUIConfigManager guiConfigManager;
    private FileConfiguration classConfig;
    private ClassManager classManager;
    private SimilarDungeonManager similarDungeonManager;
    private OrbManager orbManager;
    private RewardManager rewardManager;

    public static DungeonPlugin instance;

    @Override
    @NotNull
    protected PluginDetails getDefaultDetails() {
        return PluginDetails.create("Dungeons", new String[]{"ada", "dungeon", "dungeons", "dungeonarena"})
            .setConfigClass(Config.class)
            .setPermissionsClass(Perms.class);
    }

    @Override
    protected void addRegistries() {
        this.registerLang(Lang.class);
    }

    @Override
    protected boolean disableCommandManager() {
        return true;
    }

    @Override
    public void enable() {
        if (!this.loadInternals()) return;

        this.loadEngine();

        instance = this;

        this.dataHandler = new DataHandler(this);
        this.dataHandler.setup();

        this.userManager = new UserManager(this, this.dataHandler);
        this.userManager.setup();

        this.selectionManager = new SelectionManager(this);
        this.selectionManager.setup();

        this.mobManager = new MobManager(this);
        this.mobManager.setup();

        this.kitManager = new KitManager(this);
        this.kitManager.setup();

        this.dungeonManager = new DungeonManager(this);
        this.dungeonManager.setup();

        this.dungeonSetup = new DungeonSetup(this);
        this.dungeonSetup.setup();

        this.partyManager = new PartyManager();

        this.soloManager = new SoloManager();

        this.guiConfigManager = new GUIConfigManager(this);

        this.similarDungeonManager = new SimilarDungeonManager(this);

        this.rewardManager =  new RewardManager(this);

        File classFile = new File(getDataFolder(), "class.yml");

        if (!classFile.exists()) {
            saveResource("class.yml", false);
        }

        classConfig = YamlConfiguration.loadConfiguration(classFile);

        classManager = new ClassManager(this, classConfig);

        File orbFile = new File(getDataFolder(), "orb.yml");

        if (!orbFile.exists()) {
            saveResource("orb.yml", false);
        }

        FileConfiguration orbConfig = YamlConfiguration.loadConfiguration(orbFile);
        orbManager = new OrbManager(this, orbConfig);
        Bukkit.getPluginManager().registerEvents(new OrbListener(this, orbManager), this);

        this.loadCommands();

        if (Plugins.hasPlaceholderAPI()) {
            PlaceholderHook.setup(this);
            new VisualClassPlaceholder().register();
        }
        if (Plugins.isInstalled(HookId.MCMMO)) {
            McMMOHook.setup();
        }
        FinishChestRewardManager.init(this);
        getServer().getPluginManager().registerEvents(new FinishChestListener(), this);


        Bukkit.getPluginManager().registerEvents(new SpecialButtonListener(), this);
        Bukkit.getPluginManager().registerEvents(new PartyFinderListener(), this);
        Bukkit.getPluginManager().registerEvents(new PartyDetailsListener(), this);
        Bukkit.getPluginManager().registerEvents(new KickPlayerListener(), this);
        Bukkit.getPluginManager().registerEvents(new ReadyCheckListener(), this);
        Bukkit.getPluginManager().registerEvents(new FinishedChestRewardListener(), this);

    }

    @Override
    public void disable() {
        if (Plugins.hasPlaceholderAPI()) {
            PlaceholderHook.shutdown();
        }

        if (this.dungeonSetup != null) this.dungeonSetup.shutdown();
        if (this.dungeonManager != null) this.dungeonManager.shutdown();
        if (this.mobManager != null) this.mobManager.shutdown();
        if (this.kitManager != null) this.kitManager.shutdown();
        if (this.selectionManager != null) this.selectionManager.shutdown();

        if (this.userManager != null) this.userManager.shutdown();
        if (this.dataHandler != null) this.dataHandler.shutdown();

        NumberComparators.clear();
        ConditionRegistry.clear();
        ActionRegistry.clear();
        TaskRegistry.clear();
        ScaleBaseRegistry.clear();
        MobRegistry.clear();
        LevelRegistry.clear();
        PetRegistry.clear();
        DungeonEntityBridge.clear();
        MobVariantRegistry.clear();
        CriteriaRegistry.clear();
        GodPluginRegistry.clear();
        BoardPluginRegistry.clear();
        Keys.clear();
        DungeonsAPI.clear();
        FinishChestRewardManager.shutdown();
    }

    private boolean loadInternals() {
        String bukkitVersion = this.getServer().getBukkitVersion();
        String minecraftVersion = this.resolveMinecraftVersion();
        if (!this.isSupportedServerVersion(minecraftVersion)) {
            this.warn("Unrecognized server version '" + bukkitVersion + "' (detected Minecraft version: '" + minecraftVersion + "'). Trying bundled internals anyway.");
        }

        this.internals = this.loadVersionedInternals();
        if (this.internals == null) {
            this.warn("Version-specific NMS module unavailable, using Paper fallback internals.");
            this.internals = new PaperDungeonNMS();
        }

        return true;
    }

    private boolean isSupportedServerVersion(@NotNull String minecraftVersion) {
        return minecraftVersion.equals("1.21") || minecraftVersion.startsWith("1.21.");
    }

    @NotNull
    private String resolveMinecraftVersion() {
        String minecraftVersion = this.getServer().getMinecraftVersion();
        if (!minecraftVersion.isBlank()) {
            return minecraftVersion.trim();
        }

        String serverVersion = this.getServer().getVersion();
        Matcher matcher = MINECRAFT_VERSION_PATTERN.matcher(serverVersion);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        String bukkitVersion = this.getServer().getBukkitVersion();
        String rawVersion = bukkitVersion.split("-")[0].trim();
        return rawVersion.isEmpty() ? "unknown" : rawVersion;
    }

    @Nullable
    private DungeonNMS loadVersionedInternals() {
        try {
            Class<?> clazz = Class.forName("su.nightexpress.dungeons.nms.mc_1_21_11.MC_1_21_11");
            Object instance = clazz.getDeclaredConstructor().newInstance();
            if (instance instanceof DungeonNMS dungeonNMS) {
                this.info("Loaded full NMS internals for MC_1_21_11.");
                return dungeonNMS;
            }

            this.warn("NMS internals class does not implement DungeonNMS: " + clazz.getName());
            return null;
        }
        catch (Throwable throwable) {
            this.warn("Failed to initialize version-specific NMS internals: " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
            return null;
        }
    }

    private void loadEngine() {
        DungeonsAPI.load(this);
        Keys.load(this);
        GodPluginRegistry.load(this);
        BoardPluginRegistry.load(this);
        CriteriaRegistry.load(this);
        MobRegistry.load(this);
        LevelRegistry.load(this);
        PetRegistry.load(this);
        MobVariantRegistry.load();
        NumberComparators.load();
        ConditionRegistry.load();
        ActionRegistry.load();
        TaskRegistry.load();
        ScaleBaseRegistry.load();
    }

    private void loadCommands() {
        this.rootCommand = NightCommand.forPlugin(this, builder -> {
            BaseCommands.load(this, builder);
            SetupCommands.load(this, builder);
            KitCommands.load(this, builder);
        });
    }

    @NotNull
    public DataHandler getDataHandler() {
        return this.dataHandler;
    }

    @NotNull
    public UserManager getUserManager() {
        return this.userManager;
    }

    @NotNull
    public SelectionManager getSelectionManager() {
        return this.selectionManager;
    }

    @NotNull
    public DungeonManager getDungeonManager() {
        return this.dungeonManager;
    }

    @NotNull
    public DungeonSetup getDungeonSetup() {
        return this.dungeonSetup;
    }

    @NotNull
    public MobManager getMobManager() {
        return this.mobManager;
    }

    @NotNull
    public KitManager getKitManager() {
        return this.kitManager;
    }

    @NotNull
    public DungeonNMS getInternals() {
        return this.internals;
    }

    @NotNull
    public PartyManager getPartyManager() { return this.partyManager; }

    @NotNull
    public SoloManager getSoloManager() { return this.soloManager; }

    public GUIConfigManager getGUIConfigManager() {
        return guiConfigManager;
    }

    public ClassManager getClassManager() {
        return classManager;
    }

    public OrbManager getOrbManager() {
        return orbManager;
    }

    public RewardManager getRewardManager() { return rewardManager; }

    @NotNull
    public SimilarDungeonManager getSimilarDungeonManager() {
        return this.similarDungeonManager;
    }

}
