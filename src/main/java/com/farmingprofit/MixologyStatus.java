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
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of the player's Mastering Mixology progress towards a set of reward goals.
 *
 * <p>The plugin builds this on the CLIENT thread (resin balances come from
 * {@code client.getVarbitValue}) and hands the finished, frozen object to the Swing panel, which
 * renders it on the EDT without touching the client. Like {@link HerbPatchStatus} it carries no
 * behaviour beyond the convenience accessors the panel needs.
 *
 * <p>Resin balance varbits (verified against RuneLite {@code net.runelite.api.gameval.VarbitID}):
 * {@code MM_AVAILABLE_MOX=11431}, {@code MM_AVAILABLE_AGA=11432}, {@code MM_AVAILABLE_LYE=11433}.
 * Note these are the hopper/available paste counts; the player's spendable resin balance lives in
 * varbits 4416 (Mox), 4415 (Aga), 4414 (Lye) per the easy-mixology plugin. The plugin decides which
 * to read; this DTO only stores the resulting numbers.
 */
final class MixologyStatus
{
	/** Per-colour line the panel renders as one row. */
	static final class ColourLine
	{
		final Mixology.Resin colour;
		/** Human display name, e.g. "Mox". */
		final String colourName;
		final int balance;
		final int goal;
		final int deficit;
		final int pasteNeeded;
		/** Best herb for this colour, or {@code null} when none / goal already met. */
		final String bestHerbName;
		final int herbsNeeded;
		final boolean met;

		ColourLine(Mixology.Resin colour, int balance, int goal, int deficit, int pasteNeeded,
			String bestHerbName, int herbsNeeded, boolean met)
		{
			this.colour = colour;
			this.colourName = colour.displayName;
			this.balance = balance;
			this.goal = goal;
			this.deficit = deficit;
			this.pasteNeeded = pasteNeeded;
			this.bestHerbName = bestHerbName;
			this.herbsNeeded = herbsNeeded;
			this.met = met;
		}
	}

	private final List<ColourLine> lines;
	private final boolean hasGoals;
	private final boolean allMet;
	private final double resinPerPaste;

	private MixologyStatus(List<ColourLine> lines, boolean hasGoals, boolean allMet,
		double resinPerPaste)
	{
		this.lines = Collections.unmodifiableList(lines);
		this.hasGoals = hasGoals;
		this.allMet = allMet;
		this.resinPerPaste = resinPerPaste;
	}

	/** The three per-colour rows (Mox, Aga, Lye), in {@link Mixology.Resin} order. */
	List<ColourLine> getLines()
	{
		return lines;
	}

	/** True if any colour has a positive goal; false means the panel should show balances only. */
	boolean hasGoals()
	{
		return hasGoals;
	}

	/** True if there are goals and every colour's balance meets its goal. */
	boolean isAllMet()
	{
		return hasGoals && allMet;
	}

	/** The resin-per-paste factor used to derive paste/herb estimates. */
	double getResinPerPaste()
	{
		return resinPerPaste;
	}

	/**
	 * Build a status from live balances and a per-colour plan (typically
	 * {@link Mixology#plan(Mixology.Balances, Mixology.Balances, double)}). Runs on the client
	 * thread; the returned object is immutable and safe to pass to the EDT.
	 */
	static MixologyStatus of(Map<Mixology.Resin, Mixology.ColourPlan> plan, double resinPerPaste)
	{
		List<ColourLine> lines = new ArrayList<>(Mixology.Resin.values().length);
		boolean hasGoals = false;
		boolean allMet = true;
		for (Mixology.Resin colour : Mixology.Resin.values())
		{
			Mixology.ColourPlan p = plan.get(colour);
			if (p == null)
			{
				continue;
			}
			String herbName = p.bestHerb == null ? null : p.bestHerb.name;
			lines.add(new ColourLine(colour, p.balance, p.goal, p.deficit, p.pasteNeeded,
				herbName, p.herbsNeeded, p.isMet()));
			if (p.goal > 0)
			{
				hasGoals = true;
				if (!p.isMet())
				{
					allMet = false;
				}
			}
		}
		return new MixologyStatus(lines, hasGoals, allMet, resinPerPaste);
	}
}
