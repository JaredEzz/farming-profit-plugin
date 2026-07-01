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

/**
 * Immutable status of one herb patch for the live run helper: which herb is in it, whether it's
 * ready/growing/diseased, and (for growing patches) when it will be ready. Built on the client
 * thread from the live varbit / persisted timetracking snapshot, then rendered on the EDT.
 */
final class HerbPatchStatus
{
	enum State
	{
		READY, GROWING, DISEASED, DEAD, EMPTY
	}

	final String patchName;
	final Crop crop;
	final State state;
	/** Epoch seconds the patch becomes harvestable; 0 when not growing / unknown. */
	final long readyAtEpochSeconds;
	/** Growing snapshot past its grow window or older than a full cycle — show "verify". */
	final boolean staleVerify;
	/** In-game item representing the patch's location (teleport item), for the location icon. */
	final int teleportItemId;

	HerbPatchStatus(String patchName, Crop crop, State state, long readyAtEpochSeconds,
		boolean staleVerify, int teleportItemId)
	{
		this.patchName = patchName;
		this.crop = crop;
		this.state = state;
		this.readyAtEpochSeconds = readyAtEpochSeconds;
		this.staleVerify = staleVerify;
		this.teleportItemId = teleportItemId;
	}

	boolean isReady()
	{
		return state == State.READY;
	}

	boolean blocked()
	{
		return state == State.DISEASED || state == State.DEAD;
	}

	/** Growing with a real ready estimate — included in the "next run" countdown. */
	boolean countsTowardRun()
	{
		return state == State.GROWING && readyAtEpochSeconds > 0;
	}
}
