/*
 * Copyright (c) 2018, Mika Kuijpers <github.com/mkuijpers>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.farmingprofit;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Static game data and pure helper math for the Mastering Mixology minigame (Varlamore). No
 * client or Swing imports: this is data plus arithmetic so it can be unit-tested in isolation. The
 * plugin reads the live resin balances on the client thread and feeds them into the methods here;
 * the panel renders the resulting {@link MixologyStatus}.
 *
 * <p>All numbers are verified against the OSRS Wiki "Mastering Mixology" page (herb/paste table and
 * reward shop cost table) and cross-checked against the {@code VM9797/easy-mixology} plugin's
 * {@code MixologyRewards} enum. See the class doc on each table for the specific source.
 */
final class Mixology
{
	/** The three resin/paste colours of the minigame. */
	enum Resin
	{
		MOX("Mox"),
		AGA("Aga"),
		LYE("Lye");

		final String displayName;

		Resin(String displayName)
		{
			this.displayName = displayName;
		}
	}

	/**
	 * One refinable herb and the paste it yields. Verified against the OSRS Wiki "Mastering
	 * Mixology" herb/paste table (https://oldschool.runescape.wiki/w/Mastering_Mixology).
	 *
	 * <p>Important: contrary to a common misconception, each herb on the wiki table yields paste of
	 * exactly ONE colour (the amount shown is the paste obtained from refining one clean herb /
	 * unfinished potion of that herb). No herb yields paste in multiple colours. We therefore model
	 * a herb as a single (resin, amount) pair; the other two colours are implicitly zero.
	 */
	static final class Herb
	{
		final String name;
		final Resin resin;
		/** Paste of {@link #resin} obtained from one herb. */
		final int paste;

		Herb(String name, Resin resin, int paste)
		{
			this.name = name;
			this.resin = resin;
			this.paste = paste;
		}

		int pasteOf(Resin r)
		{
			return r == resin ? paste : 0;
		}
	}

	// Herbs in standard Herblore order. Source: OSRS Wiki "Mastering Mixology" herb/paste table
	// (verified 2026-06). Each herb -> single paste colour and amount per refined herb.
	static final List<Herb> HERBS = new ArrayList<>();

	static
	{
		//                       name            resin       paste
		HERBS.add(new Herb("Guam leaf",     Resin.MOX, 10));
		HERBS.add(new Herb("Marrentill",    Resin.MOX, 13));
		HERBS.add(new Herb("Tarromin",      Resin.MOX, 15));
		HERBS.add(new Herb("Harralander",   Resin.MOX, 20));
		HERBS.add(new Herb("Ranarr weed",   Resin.LYE, 26));
		HERBS.add(new Herb("Toadflax",      Resin.LYE, 32));
		HERBS.add(new Herb("Irit leaf",     Resin.AGA, 30));
		HERBS.add(new Herb("Avantoe",       Resin.LYE, 30));
		HERBS.add(new Herb("Huasca",        Resin.AGA, 20));
		HERBS.add(new Herb("Kwuarm",        Resin.LYE, 33));
		HERBS.add(new Herb("Snapdragon",    Resin.LYE, 40));
		HERBS.add(new Herb("Cadantine",     Resin.AGA, 34));
		HERBS.add(new Herb("Lantadyme",     Resin.AGA, 40));
		HERBS.add(new Herb("Dwarf weed",    Resin.AGA, 42));
		HERBS.add(new Herb("Torstol",       Resin.AGA, 44));
	}

	/**
	 * One reward purchasable from Mixing Hooks for resin. Costs verified against the OSRS Wiki
	 * "Mastering Mixology" reward shop table and the {@code easy-mixology} {@code MixologyRewards}
	 * enum (identical values).
	 */
	static final class Reward
	{
		final String name;
		final int moxCost;
		final int agaCost;
		final int lyeCost;

		Reward(String name, int moxCost, int agaCost, int lyeCost)
		{
			this.name = name;
			this.moxCost = moxCost;
			this.agaCost = agaCost;
			this.lyeCost = lyeCost;
		}

		int costOf(Resin r)
		{
			switch (r)
			{
				case MOX:
					return moxCost;
				case AGA:
					return agaCost;
				case LYE:
					return lyeCost;
				default:
					return 0;
			}
		}
	}

	// Reward shop costs (Mox, Aga, Lye). Source: OSRS Wiki "Mastering Mixology" reward table,
	// cross-checked against VM9797/easy-mixology MixologyRewards (verified 2026-06).
	static final List<Reward> REWARDS = new ArrayList<>();

	static
	{
		//                          name                       mox    aga    lye
		REWARDS.add(new Reward("Apprentice potion pack",      420,    70,    30));
		REWARDS.add(new Reward("Adept potion pack",           180,   440,    70));
		REWARDS.add(new Reward("Expert potion pack",          410,   320,   480));
		REWARDS.add(new Reward("Prescription goggles",       8600,  7000,  9350));
		REWARDS.add(new Reward("Alchemist labcoat",          2250,  2800,  3700));
		REWARDS.add(new Reward("Alchemist pants",            2250,  2800,  3700));
		REWARDS.add(new Reward("Alchemist gloves",           2250,  2800,  3700));
		REWARDS.add(new Reward("Reagent pouch",             13800, 11200, 15100));
		REWARDS.add(new Reward("Potion storage",             7750,  6300,  8950));
		REWARDS.add(new Reward("Chugging barrel",           17250, 14000, 18600));
		REWARDS.add(new Reward("Alchemist's amulet",         6900,  5650,  7400));
		REWARDS.add(new Reward("Aldarium",                     80,    60,    90));
	}

	/** Default ratio of spendable resin gained per paste deposited into the hopper. */
	static final double DEFAULT_RESIN_PER_PASTE = 0.7;

	/** Immutable bundle of the three live resin balances (spendable resin, not hopper paste). */
	static final class Balances
	{
		final int mox;
		final int aga;
		final int lye;

		Balances(int mox, int aga, int lye)
		{
			this.mox = mox;
			this.aga = aga;
			this.lye = lye;
		}

		int of(Resin r)
		{
			switch (r)
			{
				case MOX:
					return mox;
				case AGA:
					return aga;
				case LYE:
					return lye;
				default:
					return 0;
			}
		}
	}

	/** A reward looked up by display name, or {@code null} if no such reward exists. */
	static Reward rewardByName(String name)
	{
		for (Reward r : REWARDS)
		{
			if (r.name.equalsIgnoreCase(name))
			{
				return r;
			}
		}
		return null;
	}

	/**
	 * Total resin cost across a selection of goals, summed per colour. Each goal is a reward name
	 * mapped to a desired count (e.g. 3 aldariums); unknown names and non-positive counts are
	 * ignored.
	 */
	static Balances totalGoalCost(Map<String, Integer> goals)
	{
		int mox = 0, aga = 0, lye = 0;
		if (goals != null)
		{
			for (Map.Entry<String, Integer> e : goals.entrySet())
			{
				int count = e.getValue() == null ? 0 : e.getValue();
				if (count <= 0)
				{
					continue;
				}
				Reward r = rewardByName(e.getKey());
				if (r == null)
				{
					continue;
				}
				mox += r.moxCost * count;
				aga += r.agaCost * count;
				lye += r.lyeCost * count;
			}
		}
		return new Balances(mox, aga, lye);
	}

	/** Remaining resin needed for {@code colour}: {@code max(0, goal - have)}. */
	static int deficit(Resin colour, Balances have, Balances goal)
	{
		return Math.max(0, goal.of(colour) - have.of(colour));
	}

	/** The herb that yields the most paste of {@code colour} per refined herb, or {@code null}. */
	static Herb bestHerbFor(Resin colour)
	{
		Herb best = null;
		for (Herb h : HERBS)
		{
			int p = h.pasteOf(colour);
			if (p > 0 && (best == null || p > best.pasteOf(colour)))
			{
				best = h;
			}
		}
		return best;
	}

	/**
	 * Paste of {@code colour} that must be deposited to clear a resin deficit, given the
	 * {@code resinPerPaste} conversion factor. Returns 0 if there is no deficit.
	 */
	static int pasteNeeded(int resinDeficit, double resinPerPaste)
	{
		if (resinDeficit <= 0)
		{
			return 0;
		}
		if (resinPerPaste <= 0)
		{
			return Integer.MAX_VALUE;
		}
		return (int) Math.ceil(resinDeficit / resinPerPaste);
	}

	/**
	 * Number of {@code herb} that must be refined to produce {@code pasteNeeded} paste of its
	 * colour, rounded up. Returns 0 if no paste is needed; {@link Integer#MAX_VALUE} if the herb
	 * yields no paste of the relevant colour.
	 */
	static int herbsNeeded(Herb herb, int pasteNeeded)
	{
		if (pasteNeeded <= 0)
		{
			return 0;
		}
		if (herb == null || herb.paste <= 0)
		{
			return Integer.MAX_VALUE;
		}
		return (int) Math.ceil((double) pasteNeeded / herb.paste);
	}

	/**
	 * Compute a per-colour breakdown for the given balances and goal using the default
	 * {@link #DEFAULT_RESIN_PER_PASTE} factor.
	 */
	static Map<Resin, ColourPlan> plan(Balances have, Balances goal)
	{
		return plan(have, goal, DEFAULT_RESIN_PER_PASTE);
	}

	/**
	 * Compute a per-colour breakdown (balance, goal, deficit, paste needed, best herb and herb
	 * count) for the given balances and goal using {@code resinPerPaste}.
	 */
	static Map<Resin, ColourPlan> plan(Balances have, Balances goal, double resinPerPaste)
	{
		Map<Resin, ColourPlan> out = new EnumMap<>(Resin.class);
		for (Resin colour : Resin.values())
		{
			int balance = have.of(colour);
			int target = goal.of(colour);
			int def = Math.max(0, target - balance);
			int paste = pasteNeeded(def, resinPerPaste);
			Herb best = bestHerbFor(colour);
			int herbs = herbsNeeded(best, paste);
			out.put(colour, new ColourPlan(colour, balance, target, def, paste, best, herbs));
		}
		return out;
	}

	/** Pure result of {@link #plan} for one resin colour. */
	static final class ColourPlan
	{
		final Resin colour;
		final int balance;
		final int goal;
		final int deficit;
		final int pasteNeeded;
		final Herb bestHerb;
		final int herbsNeeded;

		ColourPlan(Resin colour, int balance, int goal, int deficit, int pasteNeeded, Herb bestHerb,
			int herbsNeeded)
		{
			this.colour = colour;
			this.balance = balance;
			this.goal = goal;
			this.deficit = deficit;
			this.pasteNeeded = pasteNeeded;
			this.bestHerb = bestHerb;
			this.herbsNeeded = herbsNeeded;
		}

		/** True once the balance meets or exceeds the goal (no deficit). */
		boolean isMet()
		{
			return deficit <= 0;
		}
	}

	private Mixology()
	{
	}
}
