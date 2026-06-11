package com.customweaponsfx;

import java.util.EnumSet;
import java.util.Set;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TriggerGroupTest
{
	@Test
	public void serializeDeserializeRoundTrips()
	{
		Set<Triggers> original = EnumSet.of(Triggers.REGULAR_HIT, Triggers.KILL, Triggers.SPECIAL_MAX);
		String serialized = TriggerGroup.serializeTriggers(original);
		assertEquals(original, TriggerGroup.deserializeTriggers(serialized));
	}

	@Test
	public void emptySetSerializesToEmptyString()
	{
		assertEquals("", TriggerGroup.serializeTriggers(EnumSet.noneOf(Triggers.class)));
		assertEquals("", TriggerGroup.serializeTriggers(null));
	}

	@Test
	public void deserializeHandlesNullBlankAndWhitespace()
	{
		assertTrue(TriggerGroup.deserializeTriggers(null).isEmpty());
		assertTrue(TriggerGroup.deserializeTriggers("").isEmpty());
		assertTrue(TriggerGroup.deserializeTriggers("   ").isEmpty());
		// surrounding whitespace and empty entries are tolerated
		assertEquals(EnumSet.of(Triggers.ALL),
			TriggerGroup.deserializeTriggers(" ALL ,, "));
	}

	@Test
	public void deserializeIgnoresUnknownTokensButKeepsValidOnes()
	{
		// Forward/backward compat: an unknown trigger name (e.g. removed enum value) must not
		// blow away the rest of a user's saved group.
		Set<Triggers> result = TriggerGroup.deserializeTriggers("REGULAR_HIT,SOMETHING_REMOVED,KILL");
		assertEquals(EnumSet.of(Triggers.REGULAR_HIT, Triggers.KILL), result);
	}

	@Test
	public void constructorDefaultsNullArgsToEmptyCollections()
	{
		TriggerGroup g = new TriggerGroup(null, null, 50);
		assertTrue(g.getTriggers().isEmpty());
		assertTrue(g.getSounds().isEmpty());
		assertEquals(50, g.getChance());
	}
}
