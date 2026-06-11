package com.customweaponsfx;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CustomWeaponSfxConfigStoreTest
{
	/** In-memory {@link CustomWeaponSfxConfigStore.Backend} mirroring ConfigManager string semantics. */
	private static final class MapBackend implements CustomWeaponSfxConfigStore.Backend
	{
		final Map<String, String> map = new HashMap<>();

		@Override
		public String get(String key)
		{
			return map.get(key);
		}

		@Override
		public void set(String key, Object value)
		{
			// ConfigManager persists everything as strings; mirror that here.
			map.put(key, String.valueOf(value));
		}

		@Override
		public void unset(String key)
		{
			map.remove(key);
		}
	}

	private MapBackend backend;
	private CustomWeaponSfxConfigStore store;

	@Before
	public void setUp()
	{
		backend = new MapBackend();
		store = new CustomWeaponSfxConfigStore(backend);
	}

	private static TriggerGroup group(java.util.Set<Triggers> triggers, int chance, SoundEntry... sounds)
	{
		List<SoundEntry> list = new ArrayList<>();
		for (SoundEntry s : sounds) list.add(s);
		return new TriggerGroup(triggers, list, chance);
	}

	private static void assertGroupEquals(TriggerGroup expected, TriggerGroup actual)
	{
		assertEquals(expected.getTriggers(), actual.getTriggers());
		assertEquals(expected.getChance(), actual.getChance());
		assertEquals(expected.getSounds().size(), actual.getSounds().size());
		for (int i = 0; i < expected.getSounds().size(); i++)
		{
			assertEquals(expected.getSounds().get(i).getSoundFile(), actual.getSounds().get(i).getSoundFile());
			assertEquals(expected.getSounds().get(i).getVolume(), actual.getSounds().get(i).getVolume());
		}
	}

	@Test
	public void weaponRoundTripsThroughSaveAndLoad()
	{
		List<TriggerGroup> groups = new ArrayList<>();
		groups.add(group(EnumSet.of(Triggers.REGULAR_HIT, Triggers.KILL), 80,
			new SoundEntry("bundled:bonk", 50), new SoundEntry("my-clip", 100)));
		groups.add(group(EnumSet.of(Triggers.SPECIAL_MAX), 25, new SoundEntry("", 75)));

		WeaponEntry entry = new WeaponEntry(1234, "Test Weapon", groups);
		entry.setEnabled(false);

		store.saveWeapon(entry);
		store.saveWeaponIds(java.util.Collections.singletonList(entry));

		List<WeaponEntry> loaded = store.loadWeapons();
		assertEquals(1, loaded.size());

		WeaponEntry back = loaded.get(0);
		assertEquals(1234, back.getItemId());
		assertEquals("Test Weapon", back.getWeaponName());
		assertFalse(back.isEnabled());
		assertEquals(2, back.getGroups().size());
		assertGroupEquals(groups.get(0), back.getGroups().get(0));
		assertGroupEquals(groups.get(1), back.getGroups().get(1));
	}

	@Test
	public void defaultGroupsRoundTrip()
	{
		String prefix = CustomWeaponSfxPanel.RECEIVED_GROUPS_PREFIX;
		List<TriggerGroup> groups = new ArrayList<>();
		groups.add(group(EnumSet.of(Triggers.REGULAR_ZERO), 100, new SoundEntry("ouch", 60)));

		store.saveDefaultGroups(prefix, groups);

		List<TriggerGroup> loaded = store.loadDefaultGroups(prefix);
		assertEquals(1, loaded.size());
		assertGroupEquals(groups.get(0), loaded.get(0));
	}

	@Test
	public void missingDefaultGroupsLoadEmpty()
	{
		assertTrue(store.loadDefaultGroups(CustomWeaponSfxPanel.GLOBAL_WEAPON_GROUPS_PREFIX).isEmpty());
	}

	@Test
	public void loadGroupAppliesDefaultsForMissingFields()
	{
		// Only a groupCount + triggers persisted; chance/volume/soundCount should fall back to defaults.
		backend.set("globalWeapon_groupCount", 1);
		backend.set("globalWeapon_group_0_triggers", "ALL");

		List<TriggerGroup> loaded = store.loadDefaultGroups("globalWeapon");
		assertEquals(1, loaded.size());
		TriggerGroup g = loaded.get(0);
		assertEquals(EnumSet.of(Triggers.ALL), g.getTriggers());
		assertEquals(100, g.getChance());          // DEFAULT_CHANCE
		assertEquals(1, g.getSounds().size());      // DEFAULT_SOUND_COUNT
		assertEquals("", g.getSounds().get(0).getSoundFile());
		assertEquals(75, g.getSounds().get(0).getVolume()); // DEFAULT_VOLUME
	}

	@Test
	public void weaponWithMissingGroupCountIsSkipped()
	{
		backend.set("specWeaponIds", "55");
		backend.set("specWeapon_55_name", "Orphan");
		// no groupCount key — parity with old loader: weapon is dropped entirely
		assertTrue(store.loadWeapons().isEmpty());
	}

	@Test
	public void invalidWeaponIdsAreIgnoredButValidOnesKept()
	{
		WeaponEntry valid = new WeaponEntry(7, "Valid", new ArrayList<>());
		store.saveWeapon(valid);
		backend.set("specWeaponIds", "notanumber,7");

		List<WeaponEntry> loaded = store.loadWeapons();
		assertEquals(1, loaded.size());
		assertEquals(7, loaded.get(0).getItemId());
	}

	@Test
	public void removeWeaponUnsetsAllItsKeys()
	{
		List<TriggerGroup> groups = new ArrayList<>();
		groups.add(group(EnumSet.of(Triggers.REGULAR_HIT), 100, new SoundEntry("s", 10)));
		WeaponEntry entry = new WeaponEntry(99, "Doomed", groups);
		store.saveWeapon(entry);

		store.removeWeapon(entry);

		assertNull(backend.get("specWeapon_99_name"));
		assertNull(backend.get("specWeapon_99_enabled"));
		assertNull(backend.get("specWeapon_99_groupCount"));
		assertNull(backend.get("specWeapon_99_group_0_triggers"));
		assertNull(backend.get("specWeapon_99_group_0_chance"));
		assertNull(backend.get("specWeapon_99_group_0_soundCount"));
		assertNull(backend.get("specWeapon_99_group_0_sound_0"));
		assertNull(backend.get("specWeapon_99_group_0_volume_0"));
	}

	@Test
	public void resetAllWipesWeaponsAndDefaults()
	{
		List<TriggerGroup> wGroups = new ArrayList<>();
		wGroups.add(group(EnumSet.of(Triggers.KILL), 100, new SoundEntry("a", 50)));
		WeaponEntry weapon = new WeaponEntry(3, "W", wGroups);
		store.saveWeapon(weapon);
		store.saveWeaponIds(java.util.Collections.singletonList(weapon));

		List<TriggerGroup> received = new ArrayList<>();
		received.add(group(EnumSet.of(Triggers.REGULAR_ZERO), 100, new SoundEntry("r", 50)));
		store.saveDefaultGroups(CustomWeaponSfxPanel.RECEIVED_GROUPS_PREFIX, received);

		List<TriggerGroup> global = new ArrayList<>();
		global.add(group(EnumSet.of(Triggers.ALL), 100, new SoundEntry("g", 50)));
		store.saveDefaultGroups(CustomWeaponSfxPanel.GLOBAL_WEAPON_GROUPS_PREFIX, global);

		store.resetAll(java.util.Collections.singletonList(weapon),
			CustomWeaponSfxPanel.RECEIVED_GROUPS_PREFIX, received,
			CustomWeaponSfxPanel.GLOBAL_WEAPON_GROUPS_PREFIX, global);

		assertNull(backend.get("specWeaponIds"));
		assertTrue(store.loadWeapons().isEmpty());
		assertTrue(store.loadDefaultGroups(CustomWeaponSfxPanel.RECEIVED_GROUPS_PREFIX).isEmpty());
		assertTrue(store.loadDefaultGroups(CustomWeaponSfxPanel.GLOBAL_WEAPON_GROUPS_PREFIX).isEmpty());
	}

	@Test
	public void getBoolReturnsDefaultWhenAbsentAndParsesWhenPresent()
	{
		assertTrue(store.getBool("missingKey", true));
		assertFalse(store.getBool("missingKey", false));

		store.setBool("someFlag", false);
		assertFalse(store.getBool("someFlag", true));

		store.setBool("someFlag", true);
		assertTrue(store.getBool("someFlag", false));
	}

	@Test
	public void saveWeaponEnabledOnlyTouchesEnabledKey()
	{
		store.saveWeaponEnabled(42, false);
		assertEquals("false", backend.get("specWeapon_42_enabled"));
		assertNull(backend.get("specWeapon_42_name"));
		assertNull(backend.get("specWeapon_42_groupCount"));
	}
}
