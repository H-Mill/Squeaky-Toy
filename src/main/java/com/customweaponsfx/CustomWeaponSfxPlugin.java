package com.customweaponsfx;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.GameState;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.NPC;
import net.runelite.api.Projectile;
import net.runelite.api.ItemContainer;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.RuneLite;
import net.runelite.client.audio.AudioPlayer;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
		name = "Custom Weapon SFX",
		description = "Plays custom sound effects on weapon hits, misses, and max hits — configurable per weapon with triggers, volume, and chance",
		tags = {"custom", "weapon", "sound", "sfx", "squeaky", "toy", "max hit", "on hit", "damage", "miss", "zero"},
		configName = "SqueakyToyPlugin"
)
public class CustomWeaponSfxPlugin extends Plugin
{
	static final String CONFIG_GROUP = "customweaponsfx";
	static final File SOUNDS_DIR = new File(RuneLite.RUNELITE_DIR, "customweaponsfx");

	/** Names of the .wav resources bundled with the plugin (without the .wav extension). */
	static final List<String> BUNDLED_SOUNDS = List.of(
		"bonk", "punch", "shot", "squeak", "oh-baby-a-triple",
		"emotional-damage", "thats-a-lot-of-damage", "okay");

	private static final int PENDING_SPEC_TIMEOUT_TICKS = 10;
	/** Drop tracked projectiles that never produced a hitsplat after this many ticks. */
	static final int PROJECTILE_TTL_TICKS = 10;
	private static final int RECEIVED_KEY = -2;

	/**
	 * NPC IDs whose hitsplats never trigger SFX. Add IDs here to exclude more NPCs — e.g. ones
	 * whose hitsplats aren't "real" attacks (clones, summoned helpers, multi-bodied bosses, etc.).
	 * Users can exclude additional NPC ids at runtime via the panel; see {@link #userExcludedNpcIds}.
	 */
	private static final Set<Integer> EXCLUDED_NPC_IDS = Set.of(
		11706,
		11707);

	@Inject private Client client;
	@Inject private AudioPlayer audioPlayer;
	@Inject private ClientToolbar clientToolbar;
	@Inject private ClientThread clientThread;
	@Inject private ConfigManager configManager;
	@Inject private ItemManager itemManager;
	@Inject private WeaponChatboxSearch weaponSearch;

	private ExecutorService executor;
	private CustomWeaponSfxPanel panel;
	private NavigationButton navButton;
	private CustomWeaponSfxConfigStore store;

	private final List<WeaponEntry> weaponEntries = new CopyOnWriteArrayList<>();
	private final List<TriggerGroup> receivedGroups = new CopyOnWriteArrayList<>();
	private final List<TriggerGroup> globalWeaponGroups = new CopyOnWriteArrayList<>();
	private List<String> bundledSounds = new ArrayList<>();
	private List<String> availableSounds = new ArrayList<>();

	private int lastSpecPct = -1;
	private int pendingSpecItemId = -1;
	private int pendingSpecTick = -1;

	/** One-tick-delayed equipped-weapon snapshot, used to attribute a projectile to the weapon that
	 *  launched it even when the player swaps weapons on the same tick the shot was fired. */
	private final TickWeaponSnapshot launchWeapon = new TickWeaponSnapshot();

	/** User-configured NPC id exclusions, on top of {@link #EXCLUDED_NPC_IDS}. Swapped atomically. */
	private volatile Set<Integer> userExcludedNpcIds = java.util.Collections.emptySet();

	/** User-configured NPC name exclusions (lowercased, trimmed) matched against the target's name. */
	private volatile Set<String> userExcludedNpcNames = java.util.Collections.emptySet();

	private final EnumMap<SfxOption, Boolean> options = new EnumMap<>(SfxOption.class);
	private int thrallTicksRemaining = 0;
	private boolean suppressReceivedOnDeath = false;

	// Hitsplats are batched per tick so multi-hit attacks are evaluated together.
	private final HitAggregator hits = new HitAggregator();

	private final AttackWeaponTracker attackWeapon = new AttackWeaponTracker();

	// Attributes delayed magic/ranged hits to the weapon that launched the projectile, so a weapon
	// swap (or auto-retaliate melee swing) while the projectile is in flight can't steal the sound.
	private final ProjectileWeaponTracker projectiles = new ProjectileWeaponTracker();

	@Override
	protected void startUp()
	{
		executor = Executors.newSingleThreadExecutor();
		SOUNDS_DIR.mkdirs();

		store = new CustomWeaponSfxConfigStore(configManager);

		weaponEntries.clear();
		weaponEntries.addAll(store.loadWeapons());
		receivedGroups.clear();
		receivedGroups.addAll(store.loadDefaultGroups(CustomWeaponSfxPanel.RECEIVED_GROUPS_PREFIX));
		globalWeaponGroups.clear();
		globalWeaponGroups.addAll(store.loadDefaultGroups(CustomWeaponSfxPanel.GLOBAL_WEAPON_GROUPS_PREFIX));
		bundledSounds = scanBundledSounds();
		availableSounds = scanSounds();

		for (SfxOption option : SfxOption.values())
			options.put(option, store.getBool(option.configKey(), option.defaultValue()));

		userExcludedNpcIds = java.util.Collections.unmodifiableSet(
			CustomWeaponSfxConfigStore.parseNpcIds(store.getExcludedNpcIdsRaw()));
		userExcludedNpcNames = java.util.Collections.unmodifiableSet(
			CustomWeaponSfxConfigStore.parseNpcNames(store.getExcludedNpcNamesRaw()));

		panel = new CustomWeaponSfxPanel(store, itemManager, this::openWeaponSearch, this::addEquippedWeapon, this::removeWeapon, this::playSoundFile, this::resetAllData, this::refreshSounds, this::onOptionChanged, this::onExcludedNpcIdsChanged, this::onExcludedNpcNamesChanged);

		navButton = NavigationButton.builder()
			.tooltip("Custom Weapon SFX")
			.icon(loadIcon())
			.panel(panel)
			.priority(5)
			.build();
		clientToolbar.addNavigation(navButton);

		panel.rebuild(new ArrayList<>(weaponEntries), availableSounds, bundledSounds, receivedGroups, globalWeaponGroups);

		clientThread.invoke(() ->
		{
			lastSpecPct = client.getVarpValue(VarPlayerID.SA_ENERGY);
			launchWeapon.init(getEquippedWeaponId());
		});

		log.debug("Custom Weapon SFX started!");
	}

	@Override
	protected void shutDown()
	{
		executor.shutdownNow();
		executor = null;

		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		store = null;

		receivedGroups.clear();
		globalWeaponGroups.clear();
		bundledSounds.clear();

		lastSpecPct = -1;
		pendingSpecItemId = -1;
		pendingSpecTick = -1;
		launchWeapon.reset();
		thrallTicksRemaining = 0;
		suppressReceivedOnDeath = false;
		options.clear();
		userExcludedNpcIds = java.util.Collections.emptySet();
		userExcludedNpcNames = java.util.Collections.emptySet();

		hits.clear();

		attackWeapon.reset();
		projectiles.reset();

		log.debug("Custom Weapon SFX stopped!");
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		// Advance the one-tick-delayed weapon snapshot so projectiles fired this tick (rendered after this
		// GameTick) are attributed to the weapon held at the start of the tick, even after a same-tick swap.
		launchWeapon.onGameTick(getEquippedWeaponId());

		int nowSpec = client.getVarpValue(VarPlayerID.SA_ENERGY);

		if (lastSpecPct >= 0 && nowSpec < lastSpecPct)
		{
			onSpecFired();
		}
		lastSpecPct = nowSpec;

		if (pendingSpecItemId >= 0
			&& client.getTickCount() - pendingSpecTick > PENDING_SPEC_TIMEOUT_TICKS)
		{
			pendingSpecItemId = -1;
			pendingSpecTick = -1;
		}

		if (thrallTicksRemaining > 0) thrallTicksRemaining--;

		projectiles.prune(client.getTickCount());

		if (!hits.isEmpty())
		{
			for (List<PendingAttack> tickAttacks : hits.drainByTick())
				evaluatePendingAttacks(tickAttacks);
		}
		suppressReceivedOnDeath = false;
	}

	private void evaluatePendingAttacks(List<PendingAttack> pendingAttacks)
	{
		boolean specHitsplatSeen = false;
		for (PendingAttack attack : pendingAttacks)
		{
			boolean isKill = attack.actors.stream()
				.filter(a -> a instanceof Player && a != client.getLocalPlayer())
				.anyMatch(a -> ((Player) a).getHealthRatio() == 0);
			AttackOutcome outcome = attack.collapse(isKill);
			boolean suppressMax  = opt(SfxOption.IGNORE_SMALL_MAX) && outcome.allMax && outcome.maxAmount <= 3;
			boolean suppressZero = opt(SfxOption.IGNORE_ZERO_THRALL) && thrallTicksRemaining > 0;
			if (!(attack.groups == receivedGroups && suppressReceivedOnDeath))
			{
				fireMatchingGroups(attack.groups, outcome.wasSpec, outcome.anyHit, outcome.allZero, outcome.allMax,
					suppressMax, suppressZero, outcome.isKill, EnumSet.noneOf(Triggers.class));
			}

			if (opt(SfxOption.GLOBAL_ENABLED) && attack.groups != receivedGroups)
			{
				Set<Triggers> weaponCoveredTriggers = opt(SfxOption.DONT_OVERRIDE_GLOBAL)
					? EnumSet.noneOf(Triggers.class)
					: attack.groups.stream()
						.filter(g -> !g.getTriggers().isEmpty())
						.flatMap(g -> g.getTriggers().stream())
						.collect(Collectors.toCollection(() -> EnumSet.noneOf(Triggers.class)));
				fireMatchingGroups(globalWeaponGroups, outcome.wasSpec, outcome.anyHit, outcome.allZero, outcome.allMax,
					suppressMax, suppressZero, outcome.isKill, weaponCoveredTriggers);
			}

			if (outcome.wasSpec) specHitsplatSeen = true;
		}
		// Clear the spec window as soon as the spec's hitsplat has been processed
		// so the next normal attack isn't silently swallowed by the wasSpec flag.
		if (specHitsplatSeen)
		{
			pendingSpecItemId = -1;
			pendingSpecTick   = -1;
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (event.getActor() != client.getLocalPlayer()) return;
		if (client.getLocalPlayer().getAnimation() == -1) return;
		attackWeapon.onAttack(getEquippedWeaponId());
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved event)
	{
		Projectile p = event.getProjectile();
		Player me = client.getLocalPlayer();
		if (me == null) return;

		// ProjectileMoved fires every frame the projectile travels; only record it once, on spawn.
		if (p.getStartCycle() > client.getGameCycle()) return;
		if (projectiles.hasSeen(p.getStartCycle())) return;

		// Only track projectiles the local player fired.
		// fall back to matching the start tile to the player when the source actor doesn't resolve.
		boolean mine = p.getSourceActor() == me;
		if (p.getSourceActor() == null)
		{
			LocalPoint loc = me.getLocalLocation();
			mine = loc != null && p.getX() == loc.getX() && p.getY() == loc.getY();
		}
		if (!mine) return;

		// Attribute to the weapon held at the start of this tick, not live equipment: this event fires
		// after the tick's swap is applied, but the shot used the pre-swap (start-of-tick) weapon.
		int weaponId = launchWeapon.launchWeapon();
		if (weaponId < 0) return;

		// getEndCycle() is the game cycle the projectile reaches its target; matched against the game
		// clock at hit time so attribution is distance-independent (no per-tick travel estimate).
		projectiles.onProjectileSpawned(weaponId, p.getTargetActor(), p.getTargetPoint(),
			p.getEndCycle(), client.getTickCount(), pendingSpecItemId >= 0);
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Actor actor = event.getActor();
		int amount  = event.getHitsplat().getAmount();
		int tick    = client.getTickCount();

		if (actor == client.getLocalPlayer())
		{
			if (!opt(SfxOption.RECEIVED_ENABLED)) return;
			if (amount == 0 && opt(SfxOption.IGNORE_RECEIVED_ZERO_PRAYER) && isProtectionPrayerActive())
				return;
			hits.add(new DeferredHit(RECEIVED_KEY, receivedGroups, false, amount, false, tick, null));
			return;
		}

		if (!event.getHitsplat().isMine()) return;

		if (actor instanceof NPC && isExcludedNpc((NPC) actor)) return;

		boolean isMax   = TriggerEvaluator.isMaxHit(event.getHitsplat().getHitsplatType());

		// A delayed magic/ranged hit is attributed to the weapon that launched its projectile, so a
		// weapon swap (or auto-retaliate melee) in flight can't steal the sound. Melee and other
		// non-projectile "mine" hitsplats find no match and fall back to the instant tracker.
		ProjectileWeaponTracker.TrackedProjectile tp = projectiles.matchAndRemove(actor, client.getGameCycle());
		boolean wasSpec;
		int weaponId;
		if (tp != null)
		{
			weaponId = tp.weaponId;
			wasSpec  = tp.wasSpec;
		}
		else
		{
			weaponId = attackWeapon.resolveWeaponId(getEquippedWeaponId());
			wasSpec  = pendingSpecItemId >= 0;
		}
		if (weaponId < 0) return;

		WeaponEntry entry = getWeaponEntry(weaponId);
		if (entry != null && entry.isEnabled())
			hits.add(new DeferredHit(weaponId, entry.getGroups(), wasSpec, amount, isMax, tick, actor));
		else
			hits.add(new DeferredHit(weaponId, java.util.Collections.emptyList(), wasSpec, amount, isMax, tick, actor));
	}

	private boolean isExcludedNpc(NPC npc)
	{
		if (EXCLUDED_NPC_IDS.contains(npc.getId()) || userExcludedNpcIds.contains(npc.getId()))
			return true;
		String name = npc.getName();
		return name != null && userExcludedNpcNames.contains(name.trim().toLowerCase());
	}

	private void onExcludedNpcIdsChanged(String raw)
	{
		store.setExcludedNpcIds(raw);
		userExcludedNpcIds = java.util.Collections.unmodifiableSet(
			CustomWeaponSfxConfigStore.parseNpcIds(raw));
	}

	private void onExcludedNpcNamesChanged(String raw)
	{
		store.setExcludedNpcNames(raw);
		userExcludedNpcNames = java.util.Collections.unmodifiableSet(
			CustomWeaponSfxConfigStore.parseNpcNames(raw));
	}

	private void resetAllData()
	{
		store.resetAll(weaponEntries,
			CustomWeaponSfxPanel.RECEIVED_GROUPS_PREFIX, receivedGroups,
			CustomWeaponSfxPanel.GLOBAL_WEAPON_GROUPS_PREFIX, globalWeaponGroups);
		weaponEntries.clear();
		receivedGroups.clear();
		globalWeaponGroups.clear();
		userExcludedNpcIds = java.util.Collections.emptySet();
		userExcludedNpcNames = java.util.Collections.emptySet();

		for (SfxOption option : SfxOption.values())
		{
			options.put(option, option.defaultValue());
			store.setBool(option.configKey(), option.defaultValue());
		}
		if (panel != null) panel.resetToggles();

		rebuildPanel();
	}

	private boolean opt(SfxOption option)
	{
		return options.getOrDefault(option, option.defaultValue());
	}

	private void onOptionChanged(SfxOption option, boolean value)
	{
		options.put(option, value);
		store.setBool(option.configKey(), value);
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (event.getActor() != client.getLocalPlayer()) return;
		if (!opt(SfxOption.RECEIVED_ENABLED)) return;
		for (TriggerGroup group : receivedGroups)
		{
			if (!group.getTriggers().contains(Triggers.PLAYER_DEATH)) continue;
			suppressReceivedOnDeath = true;
			fireGroup(group);
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() != VarbitID.ARCEUUS_RESURRECTION_COOLDOWN) return;
		if (event.getValue() != 1) return;

		int ticks = client.getBoostedSkillLevel(Skill.MAGIC);
		if (client.getVarbitValue(VarbitID.CA_TIER_STATUS_MASTER) == 2) ticks += ticks;
		thrallTicksRemaining = ticks + 4;
	}

	private boolean isProtectionPrayerActive()
	{
		return client.isPrayerActive(Prayer.PROTECT_FROM_MELEE)
			|| client.isPrayerActive(Prayer.PROTECT_FROM_MISSILES)
			|| client.isPrayerActive(Prayer.PROTECT_FROM_MAGIC);
	}

	private void onSpecFired()
	{
		int weaponId = getEquippedWeaponId();
		if (weaponId < 0) return;

		WeaponEntry entry = getWeaponEntry(weaponId);
		if (entry == null || entry.getGroups().isEmpty()) return;

		pendingSpecItemId = weaponId;
		pendingSpecTick = client.getTickCount();
	}

	private int getEquippedWeaponId()
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equipment == null) return -1;
		Item weapon = equipment.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		return weapon == null ? -1 : weapon.getId();
	}

	private void openWeaponSearch()
	{
		clientThread.invoke(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				SwingUtilities.invokeLater(() ->
					JOptionPane.showMessageDialog(
						SwingUtilities.getWindowAncestor(client.getCanvas()),
						"You must be logged in to search for weapons.",
						"Not Logged In",
						JOptionPane.WARNING_MESSAGE
					)
				);
				return;
			}
			weaponSearch
				.onItemSelected(itemId ->
				{
					String name = client.getItemDefinition(itemId).getName();
					addWeapon(itemId, name);
				})
				.build();
		});
		client.getCanvas().requestFocusInWindow();
	}

	private void addEquippedWeapon()
	{
		clientThread.invoke(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				SwingUtilities.invokeLater(() ->
					JOptionPane.showMessageDialog(
						SwingUtilities.getWindowAncestor(client.getCanvas()),
						"You must be logged in to add an equipped weapon.",
						"Not Logged In",
						JOptionPane.WARNING_MESSAGE
					)
				);
				return;
			}
			int weaponId = getEquippedWeaponId();
			if (weaponId < 0) return;
			String name = client.getItemDefinition(weaponId).getName();
			addWeapon(weaponId, name);
		});
	}

	private void addWeapon(int itemId, String name)
	{
		if (getWeaponEntry(itemId) != null) return;

		List<TriggerGroup> groups = new ArrayList<>();
		WeaponEntry entry = new WeaponEntry(itemId, name, groups);
		weaponEntries.add(entry);

		store.saveWeapon(entry);
		store.saveWeaponIds(weaponEntries);

		rebuildPanel();
	}

	private void removeWeapon(int itemId)
	{
		WeaponEntry entry = getWeaponEntry(itemId);
		if (entry != null) store.removeWeapon(entry);
		weaponEntries.removeIf(e -> e.getItemId() == itemId);
		store.saveWeaponIds(weaponEntries);

		rebuildPanel();
	}

	private void refreshSounds()
	{
		availableSounds = scanSounds();
		rebuildPanel();
	}

	private void rebuildPanel()
	{
		CustomWeaponSfxPanel p = panel;
		if (p == null) return;
		List<WeaponEntry> snapshot = new ArrayList<>(weaponEntries);
		List<String> sounds = availableSounds;
		List<String> bundled = bundledSounds;
		SwingUtilities.invokeLater(() -> p.rebuild(snapshot, sounds, bundled, receivedGroups, globalWeaponGroups));
	}

	private WeaponEntry getWeaponEntry(int itemId)
	{
		for (WeaponEntry entry : weaponEntries)
		{
			if (entry.getItemId() == itemId) return entry;
		}
		return null;
	}

	private void fireMatchingGroups(List<TriggerGroup> groups, boolean wasSpec,
									boolean anyHit, boolean allZero, boolean allMax,
									boolean suppressMax, boolean suppressZero, boolean isKill,
									Set<Triggers> weaponCoveredTriggers)
	{
		for (TriggerGroup group : TriggerEvaluator.selectFiringGroups(groups, wasSpec, anyHit, allZero,
			allMax, suppressMax, suppressZero, isKill, weaponCoveredTriggers))
		{
			fireGroup(group);
		}
	}

	private void fireGroup(TriggerGroup group)
	{
		int chance = group.getChance();
		if (chance <= 0) return;
		if (chance < 100 && ThreadLocalRandom.current().nextInt(100) >= chance) return;
		List<SoundEntry> sounds = group.getSounds();
		if (sounds.isEmpty()) return;
		SoundEntry se = sounds.size() == 1
			? sounds.get(0)
			: sounds.get(ThreadLocalRandom.current().nextInt(sounds.size()));
		playSoundFile(se.getSoundFile(), se.getVolume());
	}

	private void playSoundFile(String soundFile, int volume)
	{
		if (executor == null || executor.isShutdown()) return;
		if (volume == 0) return;
		float gain = TriggerEvaluator.volumeToGain(volume);

		if (soundFile == null || soundFile.isEmpty() || soundFile.startsWith(CustomWeaponSfxPanel.BUNDLED_PREFIX))
		{
			String name = (soundFile == null || soundFile.isEmpty())
				? "squeak.wav"
				: soundFile.substring(CustomWeaponSfxPanel.BUNDLED_PREFIX.length()) + ".wav";
			executor.submit(() ->
			{
				try { audioPlayer.play(CustomWeaponSfxPlugin.class, name, gain); }
				catch (Exception e) { log.debug("Failed to play bundled sound {}", name, e); }
			});
		}
		else
		{
			File f = new File(SOUNDS_DIR, soundFile + ".wav");
			if (!f.exists())
			{
				log.debug("Sound file missing: {}", f.getAbsolutePath());
				return;
			}
			executor.submit(() ->
			{
				try { audioPlayer.play(f, gain); }
				catch (Exception e) { log.debug("Failed to play {}", soundFile, e); }
			});
		}
	}

	private List<String> scanSounds()
	{
		List<String> sounds = new ArrayList<>();
		File[] files = SOUNDS_DIR.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".wav"));
		if (files != null)
		{
			for (File f : files)
			{
				String name = f.getName();
				sounds.add(name.substring(0, name.length() - 4));
			}
			sounds.sort(String.CASE_INSENSITIVE_ORDER);
		}
		return sounds;
	}

	private List<String> scanBundledSounds()
	{
		List<String> sounds = new ArrayList<>(BUNDLED_SOUNDS);
		sounds.sort(String.CASE_INSENSITIVE_ORDER);
		return sounds;
	}

	private static BufferedImage loadIcon()
	{
		try
		{
			return ImageUtil.loadImageResource(CustomWeaponSfxPlugin.class, "icon.png");
		}
		catch (Exception ignored) {}

		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new Color(255, 153, 0));
		g.fillOval(0, 0, 15, 15);
		g.dispose();
		return img;
	}

}
