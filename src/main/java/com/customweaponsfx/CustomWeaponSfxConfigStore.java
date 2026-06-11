package com.customweaponsfx;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

/**
 * Single source of truth for all Custom Weapon SFX config-key construction and (de)serialization.
 * No other class should build a config key string — everything goes through this store.
 *
 * <p>The flat key scheme persisted here:
 * <ul>
 *   <li>{@code specWeaponIds} — comma-joined index of configured weapon item ids.</li>
 *   <li>Per weapon {@code specWeapon_<id>}: {@code _name}, {@code _enabled}, {@code _groupCount},
 *       plus its groups.</li>
 *   <li>A "default" section (e.g. {@code defaultReceived}, {@code globalWeapon}): {@code _groupCount},
 *       {@code _enabled}, plus its groups.</li>
 *   <li>A group {@code <section>_group_<i>}: {@code _triggers}, {@code _chance}, {@code _soundCount},
 *       and per sound {@code _sound_<j>} / {@code _volume_<j>}.</li>
 * </ul>
 *
 * <p>The store reads/writes through a small {@link Backend} seam so its serialize→deserialize
 * round-trip can be unit-tested against an in-memory map instead of a live {@code ConfigManager}.
 */
@Slf4j
class CustomWeaponSfxConfigStore
{
	/** Minimal key-value seam over {@link ConfigManager}, scoped to {@code CONFIG_GROUP}. */
	interface Backend
	{
		String get(String key);
		void set(String key, Object value);
		void unset(String key);
	}

	private static final String WEAPON_IDS_KEY = "specWeaponIds";

	private static final int DEFAULT_CHANCE = 100;
	private static final int DEFAULT_VOLUME = 75;
	private static final int DEFAULT_SOUND_COUNT = 1;

	private final Backend backend;

	CustomWeaponSfxConfigStore(ConfigManager cfg)
	{
		this(new ConfigManagerBackend(cfg));
	}

	CustomWeaponSfxConfigStore(Backend backend)
	{
		this.backend = backend;
	}

	// ----- weapons --------------------------------------------------------------------------

	List<WeaponEntry> loadWeapons()
	{
		List<WeaponEntry> result = new ArrayList<>();
		String idsStr = backend.get(WEAPON_IDS_KEY);
		if (idsStr == null || idsStr.isBlank()) return result;

		for (String idStr : idsStr.split(","))
		{
			Integer itemId = tryParse(idStr);
			if (itemId == null)
			{
				log.debug("Skipping invalid weapon ID in config: {}", idStr);
				continue;
			}

			String section = weaponSection(itemId);
			List<TriggerGroup> groups = loadGroupsOrNull(section);
			if (groups == null)
			{
				// missing or invalid groupCount — drop the weapon entirely (parity with old loader)
				continue;
			}

			String name = backend.get(section + "_name");
			WeaponEntry entry = new WeaponEntry(itemId, name != null ? name : "Weapon #" + itemId, groups);
			String enabledStr = backend.get(section + "_enabled");
			if (enabledStr != null) entry.setEnabled(Boolean.parseBoolean(enabledStr));
			result.add(entry);
		}
		return result;
	}

	/** Writes name + enabled + all groups for one weapon. Does not touch the id index. */
	void saveWeapon(WeaponEntry entry)
	{
		String section = weaponSection(entry.getItemId());
		backend.set(section + "_name", entry.getWeaponName());
		backend.set(section + "_enabled", entry.isEnabled());
		saveGroups(section, entry.getGroups());
	}

	/** Writes only the group data for a weapon (used by panel edits). */
	void saveWeaponGroups(WeaponEntry entry)
	{
		saveGroups(weaponSection(entry.getItemId()), entry.getGroups());
	}

	void saveWeaponEnabled(int itemId, boolean enabled)
	{
		backend.set(weaponSection(itemId) + "_enabled", enabled);
	}

	/** Unsets every key for one weapon. Caller is responsible for updating the id index. */
	void removeWeapon(WeaponEntry entry)
	{
		String section = weaponSection(entry.getItemId());
		clearGroups(section, entry.getGroups());
		backend.unset(section + "_name");
		backend.unset(section + "_enabled");
	}

	void saveWeaponIds(List<WeaponEntry> weapons)
	{
		String ids = weapons.stream()
			.map(e -> String.valueOf(e.getItemId()))
			.collect(Collectors.joining(","));
		backend.set(WEAPON_IDS_KEY, ids);
	}

	// ----- default sections (received / global) ---------------------------------------------

	List<TriggerGroup> loadDefaultGroups(String prefix)
	{
		List<TriggerGroup> groups = loadGroupsOrNull(prefix);
		return groups != null ? groups : new ArrayList<>();
	}

	void saveDefaultGroups(String prefix, List<TriggerGroup> groups)
	{
		saveGroups(prefix, groups);
	}

	void clearDefaultGroups(String prefix, List<TriggerGroup> groups)
	{
		clearGroups(prefix, groups);
	}

	// ----- booleans -------------------------------------------------------------------------

	boolean getBool(String key, boolean def)
	{
		String v = backend.get(key);
		return v == null ? def : Boolean.parseBoolean(v);
	}

	void setBool(String key, boolean value)
	{
		backend.set(key, value);
	}

	// ----- reset ----------------------------------------------------------------------------

	/** Wipes all weapons (keys + id index) and both default sections. Booleans are left to the caller. */
	void resetAll(List<WeaponEntry> weapons,
		String receivedPrefix, List<TriggerGroup> receivedGroups,
		String globalPrefix, List<TriggerGroup> globalGroups)
	{
		for (WeaponEntry entry : weapons) removeWeapon(entry);
		backend.unset(WEAPON_IDS_KEY);
		clearDefaultGroups(receivedPrefix, receivedGroups);
		clearDefaultGroups(globalPrefix, globalGroups);
	}

	// ----- internals ------------------------------------------------------------------------

	private static String weaponSection(int itemId)
	{
		return "specWeapon_" + itemId;
	}

	private static String groupKey(String section, int i)
	{
		return section + "_group_" + i;
	}

	/** Returns the section's groups, or {@code null} if its groupCount is missing or unparseable. */
	private List<TriggerGroup> loadGroupsOrNull(String section)
	{
		String countStr = backend.get(section + "_groupCount");
		if (countStr == null || countStr.isBlank()) return null;
		Integer count = tryParse(countStr);
		if (count == null)
		{
			log.debug("Skipping invalid groupCount for {}", section);
			return null;
		}

		List<TriggerGroup> groups = new ArrayList<>();
		for (int i = 0; i < count; i++)
			groups.add(loadGroup(groupKey(section, i)));
		return groups;
	}

	private TriggerGroup loadGroup(String gk)
	{
		Set<Triggers> triggers = TriggerGroup.deserializeTriggers(backend.get(gk + "_triggers"));
		int chance = parseIntOr(backend.get(gk + "_chance"), DEFAULT_CHANCE);
		int soundCount = parseIntOr(backend.get(gk + "_soundCount"), DEFAULT_SOUND_COUNT);

		List<SoundEntry> sounds = new ArrayList<>();
		for (int j = 0; j < soundCount; j++)
		{
			String sf = backend.get(gk + "_sound_" + j);
			int vol = parseIntOr(backend.get(gk + "_volume_" + j), DEFAULT_VOLUME);
			sounds.add(new SoundEntry(sf != null ? sf : "", vol));
		}
		return new TriggerGroup(triggers, sounds, chance);
	}

	private void saveGroups(String section, List<TriggerGroup> groups)
	{
		backend.set(section + "_groupCount", groups.size());
		for (int i = 0; i < groups.size(); i++)
			saveGroup(groupKey(section, i), groups.get(i));
	}

	private void saveGroup(String gk, TriggerGroup g)
	{
		backend.set(gk + "_triggers", TriggerGroup.serializeTriggers(g.getTriggers()));
		backend.set(gk + "_chance", g.getChance());
		List<SoundEntry> sounds = g.getSounds();
		backend.set(gk + "_soundCount", sounds.size());
		for (int j = 0; j < sounds.size(); j++)
		{
			backend.set(gk + "_sound_" + j, sounds.get(j).getSoundFile());
			backend.set(gk + "_volume_" + j, sounds.get(j).getVolume());
		}
	}

	private void clearGroups(String section, List<TriggerGroup> groups)
	{
		backend.unset(section + "_groupCount");
		for (int i = 0; i < groups.size(); i++)
			clearGroup(groupKey(section, i), groups.get(i).getSounds());
	}

	private void clearGroup(String gk, List<SoundEntry> sounds)
	{
		backend.unset(gk + "_triggers");
		backend.unset(gk + "_chance");
		backend.unset(gk + "_soundCount");
		for (int j = 0; j < sounds.size(); j++)
		{
			backend.unset(gk + "_sound_" + j);
			backend.unset(gk + "_volume_" + j);
		}
	}

	private static Integer tryParse(String s)
	{
		if (s == null) return null;
		try { return Integer.parseInt(s.trim()); }
		catch (NumberFormatException e) { return null; }
	}

	private static int parseIntOr(String s, int def)
	{
		Integer v = tryParse(s);
		return v != null ? v : def;
	}

	private static final class ConfigManagerBackend implements Backend
	{
		private final ConfigManager cfg;

		ConfigManagerBackend(ConfigManager cfg) { this.cfg = cfg; }

		@Override
		public String get(String key)
		{
			return cfg.getConfiguration(CustomWeaponSfxPlugin.CONFIG_GROUP, key);
		}

		@Override
		public void set(String key, Object value)
		{
			cfg.setConfiguration(CustomWeaponSfxPlugin.CONFIG_GROUP, key, value);
		}

		@Override
		public void unset(String key)
		{
			cfg.unsetConfiguration(CustomWeaponSfxPlugin.CONFIG_GROUP, key);
		}
	}
}
