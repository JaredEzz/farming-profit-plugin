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

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import net.runelite.client.eventbus.Subscribe;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.AnimationID;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.Skill;
import net.runelite.api.SpriteID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.vars.AccountType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.task.Schedule;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@PluginDescriptor(
	name = "Farming Profit",
	description = "Tracks farm-run profit or XP and ranks the most profitable herbs to plant",
	tags = {"farm", "farming", "runs", "run", "profit", "herb", "planner", "xp", "ironman", "money"}
)
@Slf4j
public class FarmingProfitPlugin extends Plugin
{
	// Injections
	@Inject
	private Client client;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private ItemManager itemManager;
	@Inject
	private SpriteManager spriteManager;
	@Inject
	private ClientThread clientThread;
	@Inject
	private FarmingProfitConfig config;
	@Inject
	private ConfigManager configManager;
	@Inject
	private Gson gson;
	@Inject
	private NtfyNotifier ntfyNotifier;

	@Provides
	FarmingProfitConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FarmingProfitConfig.class);
	}

	// UI elements
	private FarmingProfitPanel panel;
	private NavigationButton navButton;

	// Plugin vars
	@Getter
	private Multiset<Crop> prevCropInv;
	@Getter
	private FarmingProfitRun latestRun = null;
	@Getter
	private int latestObjID = -1;
	@Getter
	private int storedObjID = -1;
	@Getter
	private boolean startedHarvesting = false;

	// Static vars for back-up checks
	final private static int MAX_PATCH_DISTANCE = 20;
	final private static int MIN_TELEPORT_DISTANCE = 40;

	// Items that grant a herb-yield bonus. Magic secateurs count whether equipped or carried;
	// the cape must be worn (checked against the equipment container only).
	private static final int[] MAGIC_SECATEURS_IDS = {ItemID.MAGIC_SECATEURS};
	private static final int[] FARMING_CAPE_IDS = {
		ItemID.FARMING_CAPE, ItemID.FARMING_CAPET, ItemID.MAX_CAPE, ItemID.MAX_CAPE_13342};

	// Bottomless compost bucket: item 22994 (empty) / 22997 (filled); the remaining uses are stored in
	// varbit 7916 (FARMING_TOOLS_BOTTOMLESS_BUCKET_QUANTITY), valid whenever logged in.
	private static final int[] BOTTOMLESS_BUCKET_IDS = {22994, 22997};
	private static final int VARBIT_BUCKET_QUANTITY = 7916;

	// Flags
	private boolean START_HARVEST_NEXT_GAMETICK = false;
	private boolean FINISH_HARVEST_NEXT_GAMETICK = false;

	// Herb harvests are counted from Farming XP gain (herbs may auto-deposit into a herb sack and
	// never enter the inventory, so the inventory diff misses them). These track the running XP
	// total and which herb the patch being harvested holds.
	private int lastFarmingXp = -1;
	private Crop activeHarvestCrop = Crop.UNKNOWN;
	/** True only while harvesting an actual herb patch (not an allotment/flower/hops in the same
	 * region), so Farming XP from those other patches isn't miscounted as herbs. */
	private boolean harvestingHerbPatch = false;
	// Order patches were last harvested in (region -> sequence), so the helper can list them in your
	// most recent run's route order rather than a fixed order.
	private long harvestSeq = 0;
	private final Map<Integer, Long> lastHarvestSeqByRegion = new HashMap<>();
	private boolean runsLoaded = false;
	// Last observed state of the herb patch in the player's current region, to detect when a dead
	// patch is cleared (DEAD -> empty) so it can be folded into the run like a normal patch visit.
	private final Map<Integer, HerbPatchStatus.State> lastHerbPatchState = new HashMap<>();

	// ---- ntfy "run ready" notifier state ----
	// Throttle: only evaluate readiness at most once every ~10s, even though onGameTick fires ~1.67x/s.
	private static final long NTFY_CHECK_INTERVAL_MS = 10_000L;
	private long lastNtfyCheckMs = 0L;
	// Dedup (whole-run): edge-trigger. True once we've notified for the current ready-cycle; cleared
	// when any patch is still growing (or nothing is planted), so a fresh run re-arms it. A boolean is
	// used rather than a timestamp because buildHerbRunStatus promotes past-due growing patches to
	// READY, so a fully-ready run has zero "growing" patches to derive a ready-timestamp from.
	// Volatile: written on the client thread (onGameTick) and read on the scheduler thread.
	private volatile boolean wasRunReady = false;
	// The epoch-second the whole run finishes, captured while logged in (the latest grow-finish of the
	// still-growing patches). A @Schedule task reads this to fire the notification even while you are
	// logged OUT (the client is still open, just at the login screen). 0 = nothing pending.
	private volatile long pendingRunReadyAt = 0L;
	// Dedup (per-patch): patches already announced READY this cycle, cleared when they leave READY.
	private final java.util.Set<String> notifiedReadyPatches = new java.util.HashSet<>();

	// ====================== //
	//     PLUGIN ACTIONS     //
	// ====================== //

	@Override
	protected void startUp()
	{
		log.debug("Starting Farming Profit Plugin");

		prevCropInv = HashMultiset.create();

		// Farming Profit Panel. Seed the initial mode from config only — getAccountType()
		// (used by resolveXpMode for AUTO) asserts the client thread, so it cannot run here in
		// startUp(). The clientThread.invoke(refreshDisplay) below corrects AUTO once on-thread.
		panel = new FarmingProfitPanel(itemManager, config.displayMode() == DisplayMode.XP);
		panel.setPlannerRefreshAction(() -> clientThread.invoke(this::refreshDisplay));
		panel.setRunHistoryChanged(this::saveRuns);
		spriteManager.getSpriteAsync(SpriteID.SKILL_FARMING, 0, panel::loadHeaderIcon);

		final BufferedImage icon = new SkillIconManager().getSkillImage(Skill.FARMING, false);

		navButton = NavigationButton.builder()
			.tooltip("Farming Profit")
			.icon(icon)
			.priority(5)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);

		// Populate the planner with whatever state is already available.
		clientThread.invoke(this::refreshDisplay);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		// RuneLite reuses this plugin instance across a disable->enable cycle, so reset load state and
		// the transient mid-harvest flags. Without resetting runsLoaded, the next startUp()'s fresh
		// (empty) panel would never re-import the on-disk run history (loadRuns early-returns).
		runsLoaded = false;
		latestRun = null;
		startedHarvesting = false;
		harvestingHerbPatch = false;
		activeHarvestCrop = Crop.UNKNOWN;
		lastFarmingXp = -1;
		storedObjID = -1;
		latestObjID = -1;
		START_HARVEST_NEXT_GAMETICK = false;
		FINISH_HARVEST_NEXT_GAMETICK = false;
		lastNtfyCheckMs = 0L;
		wasRunReady = false;
		pendingRunReadyAt = 0L;
		notifiedReadyPatches.clear();
		lastHerbPatchState.clear();
	}

	// ====================== //
	//   DISPLAY MODE / PLANNER //
	// ====================== //

	/**
	 * Resolve whether to show Farming XP rather than GE profit, honouring the configured
	 * {@link DisplayMode} (AUTO uses the account type: XP for ironmen, profit otherwise).
	 * Reads a varbit-backed value, so call on the client thread.
	 */
	private boolean resolveXpMode()
	{
		switch (config.displayMode())
		{
			case XP:
				return true;
			case PROFIT:
				return false;
			case AUTO:
			default:
				// isIronman() excludes group ironman, so check both — a GIM can't use the GE
				// either, so XP is the meaningful metric for them too.
				final AccountType type = client.getAccountType();
				return type != null && (type.isIronman() || type.isGroupIronman());
		}
	}

	/**
	 * Re-read live state (display mode, level, gear) and push it to the panel. Must run on the
	 * client thread; the Swing updates are marshalled onto the EDT.
	 */
	private void refreshDisplay()
	{
		loadRuns(); // no-op once loaded; retries until the RS profile is available
		final boolean xpMode = resolveXpMode();
		final PlannerInputs inputs = buildPlannerInputs(xpMode);
		// Rank herbs here, on the client thread, because it reads live GE prices (getItemPrice
		// asserts the client thread). The panel then only renders the result on the EDT.
		final List<HerbResult> ranked = rankHerbs(inputs);
		final boolean showGraph = config.showRunGraph();
		final java.awt.Color expectedColor = config.expectedLineColor();
		final java.awt.Color actualColor = config.actualLineColor();
		final List<HerbPatchStatus> herbStatuses = buildHerbRunStatus();
		final boolean showLocationIcons = config.showLocationIcons();
		final MixologyStatus mixology = config.mixologyEnabled() ? buildMixologyStatus() : null;
		// Bottomless compost bucket remaining uses — only surfaced when the bucket is in the inventory.
		final boolean bucketPresent = containerContains(InventoryID.INVENTORY, BOTTOMLESS_BUCKET_IDS);
		final int bucketUses = bucketPresent ? client.getVarbitValue(VARBIT_BUCKET_QUANTITY) : -1;
		SwingUtilities.invokeLater(() ->
		{
			panel.setXpMode(xpMode);
			panel.setGraphConfig(showGraph, expectedColor, actualColor);
			panel.updatePlanner(inputs, ranked);
			panel.updateHerbRun(herbStatuses, showLocationIcons);
			panel.updateMixology(mixology);
			panel.updateCompostBucket(bucketUses);
		});
	}

	private static final String TIMETRACKING = "timetracking";

	/**
	 * Build the live status of all 10 herb patches: the one in the player's current region from the
	 * live varbit, the rest from RuneLite's persisted timetracking snapshot. Computes a ready-at time
	 * for growing patches. Client thread only (reads {@code client} and RS-profile config).
	 */
	private List<HerbPatchStatus> buildHerbRunStatus()
	{
		final long now = Instant.now().getEpochSecond();
		final Integer offset = configManager.getRSProfileConfiguration(TIMETRACKING, "farmTickOffset", int.class);
		final Integer precision = configManager.getRSProfileConfiguration(TIMETRACKING, "farmTickOffsetPrecision", int.class);
		// getLocalPlayer()/getWorldLocation() can both be null during scene transitions.
		final WorldPoint playerLoc = client.getLocalPlayer() != null
			? client.getLocalPlayer().getWorldLocation() : null;
		final Integer playerRegion = playerLoc != null ? playerLoc.getRegionID() : null;

		// List patches in the order you last harvested them (your most recent run's route); patches
		// not yet harvested this session fall to the end.
		final List<HerbPatches.HerbPatch> ordered = new ArrayList<>(HerbPatches.PATCHES);
		ordered.sort(Comparator.comparingLong(
			p -> lastHarvestSeqByRegion.getOrDefault(p.regionId, Long.MAX_VALUE)));

		final List<HerbPatchStatus> out = new ArrayList<>();
		for (HerbPatches.HerbPatch p : ordered)
		{
			final long[] snap = readPatchSnapshot(p);
			Integer value = null;
			long plantedSec = 0;
			boolean liveNoAnchor = false;
			if (playerRegion != null && playerRegion == p.regionId)
			{
				value = client.getVarbitValue(p.varbit); // freshest, live
				// Only trust the snapshot timestamp if its stored stage matches the live value;
				// otherwise the live stage has advanced and the old timestamp would be wrong.
				if (snap != null && (int) snap[0] == value)
				{
					plantedSec = snap[1];
				}
				else
				{
					liveNoAnchor = true;
				}
			}
			else if (snap != null)
			{
				value = (int) snap[0];
				plantedSec = snap[1];
			}

			if (value == null)
			{
				out.add(new HerbPatchStatus(p.name, Crop.UNKNOWN, HerbPatchStatus.State.EMPTY, 0, false, iconFor(p)));
				continue;
			}

			final int v = value;
			final Crop crop = HerbPatches.decodeCrop(v);
			HerbPatchStatus.State state = HerbPatches.decodeState(v);
			long readyAt = 0;
			boolean stale = false;
			if (state == HerbPatchStatus.State.READY)
			{
				readyAt = now;
			}
			else if (state == HerbPatchStatus.State.GROWING)
			{
				final int stage = Math.max(HerbPatches.decodeStage(v), 0);
				if (plantedSec > 0)
				{
					readyAt = herbReadyAt(plantedSec, stage, offset, precision);
					if (readyAt <= now)
					{
						state = HerbPatchStatus.State.READY;
						stale = true;
						readyAt = now;
					}
					else if (plantedSec < now - 4800)
					{
						stale = true;
					}
				}
				else if (liveNoAnchor)
				{
					// Live growing but no trustworthy plant time: conservatively anchor the current
					// stage at now so it still feeds the countdown, flagged for verification.
					readyAt = herbReadyAt(now, stage, offset, precision);
					stale = true;
				}
			}
			out.add(new HerbPatchStatus(p.name, crop, state, readyAt, stale, iconFor(p)));
		}
		return out;
	}

	/**
	 * The location-icon item id for a patch: the per-patch config override when set (non-zero),
	 * otherwise the selected {@link IconStyle}'s default for that patch.
	 */
	private int iconFor(HerbPatches.HerbPatch p)
	{
		final int override = iconOverride(p.regionId);
		if (override > 0)
		{
			return override;
		}
		return config.iconStyle() == IconStyle.THEMED ? p.themedItemId : p.teleportItemId;
	}

	private int iconOverride(int regionId)
	{
		switch (regionId)
		{
			case 11062: return config.iconCatherby();
			case 12083: return config.iconFalador();
			case 10548: return config.iconArdougne();
			case 14391: return config.iconMorytania();
			case 6967:  return config.iconHosidius();
			case 6192:  return config.iconCivitas();
			case 4922:  return config.iconFarmingGuild();
			case 11321: return config.iconTrollStronghold();
			case 11325: return config.iconWeiss();
			case 15148: return config.iconHarmony();
			default:    return 0;
		}
	}

	/** Parse a patch's persisted "varbitValue:plantedUnixSeconds" snapshot, or null if absent/bad. */
	private long[] readPatchSnapshot(HerbPatches.HerbPatch p)
	{
		final String raw = configManager.getRSProfileConfiguration(TIMETRACKING, p.regionId + "." + p.varbit);
		if (raw == null)
		{
			return null;
		}
		final String[] parts = raw.split(":");
		if (parts.length != 2)
		{
			return null;
		}
		try
		{
			final long val = Integer.parseInt(parts[0].trim());
			final long ts = Long.parseLong(parts[1].trim());
			return ts > 0 ? new long[]{val, ts} : null;
		}
		catch (NumberFormatException ex)
		{
			return null;
		}
	}

	/**
	 * Epoch seconds a herb patch becomes harvestable, ported from RuneLite's
	 * {@code FarmingTracker.getTickTime}: floor the plant time to the global 20-minute farm-tick grid
	 * (shifted by the per-profile offset), then add the remaining growth ticks. All herbs use a
	 * 20-minute tick rate and 5 growth stages.
	 */
	private static long herbReadyAt(long plantedSec, int stage, Integer offset, Integer precision)
	{
		final int tick = 1200; // 20 minutes
		final int rate = 20;
		final int remaining = (5 - 1) - stage;

		long offsetSec = 0;
		if (offset != null && precision != null && (precision >= rate || precision >= 40))
		{
			offsetSec = (long) Math.floorMod(offset, rate) * 60;
		}
		final long t = plantedSec + offsetSec;
		final long aligned = t - Math.floorMod(t, tick);
		return aligned + (long) remaining * tick - offsetSec;
	}

	/**
	 * Rank every herb the player can currently plant by GP profit (or Farming XP) per run, using
	 * live GE prices. Reads {@code itemManager.getItemPrice}, so this MUST run on the client thread.
	 */
	private List<HerbResult> rankHerbs(PlannerInputs in)
	{
		final int lives = in.getCompost().getHarvestLives();
		final double itemBonus = in.itemBonusPct();

		final List<HerbResult> results = new ArrayList<>();
		for (Crop crop : Crop.values())
		{
			if (crop.getPatchType() != PatchType.HERBS)
			{
				continue;
			}
			if (crop.getFarmingLevel() > in.getFarmingLevel())
			{
				continue;
			}

			final double yieldPerPatch = HerbYield.expectedYieldPerPatch(
				crop, in.getFarmingLevel(), lives, itemBonus, in.isAttas());

			final int seedPrice = itemManager.getItemPrice(crop.getSeedId());
			final int herbPrice = itemManager.getItemPrice(crop.getHarvestedItemId());

			final double profitPerRun = (yieldPerPatch * herbPrice - (double) seedPrice * crop.getSeedAmount())
				* in.getPatches();
			final double xpPerRun = (crop.getPlantXp() + yieldPerPatch * crop.getHarvestXp()) * in.getPatches();

			results.add(new HerbResult(crop, yieldPerPatch, Math.round(profitPerRun), xpPerRun));
		}

		final Comparator<HerbResult> byValue = in.isXpMode()
			? Comparator.comparingDouble(r -> r.xpPerRun)
			: Comparator.comparingLong(r -> r.profitPerRun);
		results.sort(byValue.reversed());
		return results;
	}

	// Mastering Mixology. The player's spendable resin balance lives in these VarPlayers (RuneLite
	// VarPlayerID.MIXOLOGY_*_POINTS) — read with getVarpValue, NOT getVarbitValue. (The varbits
	// 11431-11433 are a different entity, the in-interface "available" display.)
	private static final String EASY_MIXOLOGY = "easymixology";
	private static final String MASTERING_MIXOLOGY = "masteringmixology";
	private static final int VARP_RESIN_MOX = 4416;
	private static final int VARP_RESIN_AGA = 4415;
	private static final int VARP_RESIN_LYE = 4414;

	// hex-agon Mastering Mixology stores a single goal as the RewardItem enum constant name (e.g.
	// "APPRENTICE_POTION_PACK") under masteringmixology.selectedReward; this maps each to our reward
	// display name. The repeatable rewards also honour masteringmixology.rewardQuantity.
	private static final Map<String, String> HEX_REWARD_NAMES = new HashMap<>();
	private static final java.util.Set<String> HEX_REPEATABLE = new java.util.HashSet<>(
		java.util.Arrays.asList("APPRENTICE_POTION_PACK", "ADEPT_POTION_PACK", "EXPERT_POTION_PACK",
			"ALDARIUM"));

	static
	{
		HEX_REWARD_NAMES.put("APPRENTICE_POTION_PACK", "Apprentice potion pack");
		HEX_REWARD_NAMES.put("ADEPT_POTION_PACK", "Adept potion pack");
		HEX_REWARD_NAMES.put("EXPERT_POTION_PACK", "Expert potion pack");
		HEX_REWARD_NAMES.put("PRESCRIPTION_GOGGLES", "Prescription goggles");
		HEX_REWARD_NAMES.put("ALCHEMIST_LABCOAT", "Alchemist labcoat");
		HEX_REWARD_NAMES.put("ALCHEMIST_PANTS", "Alchemist pants");
		HEX_REWARD_NAMES.put("ALCHEMIST_GLOVES", "Alchemist gloves");
		HEX_REWARD_NAMES.put("REAGENT_POUCH", "Reagent pouch");
		HEX_REWARD_NAMES.put("POTION_STORAGE", "Potion storage");
		HEX_REWARD_NAMES.put("CHUGGING_BARREL", "Chugging barrel");
		HEX_REWARD_NAMES.put("ALCHEMISTS_AMULET", "Alchemist's amulet");
		HEX_REWARD_NAMES.put("ALDARIUM", "Aldarium");
	}

	/**
	 * Build the live Mastering Mixology status: current spendable resin balances (from varbits) and,
	 * if the Easy Mixology plugin's reward goals are set, the herbs needed to reach them. Client
	 * thread only (reads {@code client.getVarbitValue}).
	 */
	private MixologyStatus buildMixologyStatus()
	{
		final Mixology.Balances have = new Mixology.Balances(
			client.getVarpValue(VARP_RESIN_MOX),
			client.getVarpValue(VARP_RESIN_AGA),
			client.getVarpValue(VARP_RESIN_LYE));
		final Mixology.Balances goalCost = Mixology.totalGoalCost(readMixologyGoals());
		final double resinPerPaste = config.resinPerPastePct() / 100.0;
		return MixologyStatus.of(Mixology.plan(have, goalCost, resinPerPaste), resinPerPaste);
	}

	/**
	 * Read the player's reward goals from the Easy Mixology plugin's config (group "easymixology"),
	 * mapping its keys to our reward names. Any missing key is simply omitted, so this degrades to an
	 * empty goal set (balances-only view) when Easy Mixology isn't installed/configured.
	 */
	private Map<String, Integer> readMixologyGoals()
	{
		final Map<String, Integer> goals = new HashMap<>();
		// hex-agon Mastering Mixology: a single selected reward (+ quantity for repeatables).
		addSelectedRewardGoal(goals);
		// Easy Mixology (alternative plugin): per-reward boolean/count goals.
		addBoolGoal(goals, "prescriptionGoggles", "Prescription goggles");
		addBoolGoal(goals, "alchemistLabcoat", "Alchemist labcoat");
		addBoolGoal(goals, "alchemistPants", "Alchemist pants");
		addBoolGoal(goals, "alchemistGloves", "Alchemist gloves");
		addBoolGoal(goals, "Reagent pouch", "Reagent pouch"); // literal key with a space in easymixology
		addBoolGoal(goals, "potionStorage", "Potion storage");
		addBoolGoal(goals, "chuggingBarrel", "Chugging barrel");
		addBoolGoal(goals, "alchemistsAmulet", "Alchemist's amulet");
		addIntGoal(goals, "apprenticePotionPackCount", "Apprentice potion pack");
		addIntGoal(goals, "adeptPotionPackCount", "Adept potion pack");
		addIntGoal(goals, "expertPotionPackCount", "Expert potion pack");
		addIntGoal(goals, "aldariumCount", "Aldarium");
		return goals;
	}

	/** Read the hex-agon Mastering Mixology selected reward goal (+ quantity for repeatable rewards). */
	private void addSelectedRewardGoal(Map<String, Integer> goals)
	{
		final String sel = configManager.getConfiguration(MASTERING_MIXOLOGY, "selectedReward", String.class);
		if (sel == null || sel.isEmpty() || "NONE".equals(sel))
		{
			return;
		}
		final String reward = HEX_REWARD_NAMES.get(sel);
		if (reward == null)
		{
			return;
		}
		int qty = 1;
		if (HEX_REPEATABLE.contains(sel))
		{
			final Integer q = configManager.getConfiguration(MASTERING_MIXOLOGY, "rewardQuantity", Integer.class);
			if (q != null && q > 0)
			{
				qty = Math.min(q, 100000);
			}
		}
		goals.merge(reward, qty, Integer::sum);
	}

	private void addBoolGoal(Map<String, Integer> goals, String key, String reward)
	{
		final Boolean v = configManager.getConfiguration(EASY_MIXOLOGY, key, Boolean.class);
		if (v != null && v)
		{
			goals.put(reward, 1);
		}
	}

	private void addIntGoal(Map<String, Integer> goals, String key, String reward)
	{
		final Integer v = configManager.getConfiguration(EASY_MIXOLOGY, key, Integer.class);
		if (v != null && v > 0)
		{
			// Clamp to Easy Mixology's own @Range max so a hand-edited config can't overflow the
			// int resin-cost arithmetic in Mixology.totalGoalCost.
			goals.put(reward, Math.min(v, 10000));
		}
	}

	private PlannerInputs buildPlannerInputs(boolean xpMode)
	{
		final boolean loggedIn = client.getGameState() == GameState.LOGGED_IN;
		int level = loggedIn ? client.getRealSkillLevel(Skill.FARMING) : 0;
		final boolean levelKnown = level > 0;
		if (!levelKnown)
		{
			level = 99;
		}

		final boolean secateurs = containerContains(InventoryID.EQUIPMENT, MAGIC_SECATEURS_IDS)
			|| containerContains(InventoryID.INVENTORY, MAGIC_SECATEURS_IDS);
		final boolean cape = containerContains(InventoryID.EQUIPMENT, FARMING_CAPE_IDS);

		return new PlannerInputs(level, levelKnown, secateurs, cape, config.plannerAttas(),
			config.plannerCompost(), config.plannerPatches(), xpMode);
	}

	private boolean containerContains(InventoryID inventoryID, int[] ids)
	{
		final ItemContainer container = client.getItemContainer(inventoryID);
		if (container == null)
		{
			return false;
		}
		for (int id : ids)
		{
			if (container.contains(id))
			{
				return true;
			}
		}
		return false;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			// Seed the XP baseline so the first harvest delta isn't garbage.
			lastFarmingXp = client.getSkillExperience(Skill.FARMING);
			loadRuns();
			refreshDisplay();
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (event.getSkill() != Skill.FARMING)
		{
			return;
		}

		// Count herb harvests from Farming XP: while a harvest is in progress, convert the XP gained
		// into a herb count using the active herb's per-harvest XP. This works whether the herb lands
		// in the inventory or a herb sack. Non-herb crops are still counted by the inventory diff.
		final int newXp = event.getXp();
		if (startedHarvesting && lastFarmingXp >= 0 && activeHarvestCrop != Crop.UNKNOWN)
		{
			final int delta = newXp - lastFarmingXp;
			final double perHerb = activeHarvestCrop.getHarvestXp();
			if (delta > 0 && perHerb > 0)
			{
				final int amount = (int) Math.round(delta / perHerb);
				if (amount > 0)
				{
					log.debug("Herb XP harvest: +{} xp -> {}x {}", delta, amount, activeHarvestCrop.getDisplayName());
					handleHarvest(activeHarvestCrop, amount);
					pushCurrentHarvest();
				}
			}
		}
		lastFarmingXp = newXp;

		refreshDisplay();
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		// Keep the compost-bucket counter and Mixology resin balances live: those change without a
		// stat/inventory event (using compost, spending resin), so refresh when their value changes.
		final int vb = event.getVarbitId();
		final int vp = event.getVarpId();
		if (vb == VARBIT_BUCKET_QUANTITY
			|| vp == VARP_RESIN_MOX || vp == VARP_RESIN_AGA || vp == VARP_RESIN_LYE)
		{
			refreshDisplay();
		}

		// Detect clearing a dead herb patch in the player's current region (the herb patch varbit is
		// region-scoped, so it only reflects the patch you're standing at). A DEAD -> non-dead/empty
		// transition means you cleared it; fold that into the run as a patch visit (see below).
		// onVarbitChanged is hot, so cheaply skip anything that isn't a herb patch varbit first.
		if (vb != 4771 && vb != 4772 && vb != 4774 && vb != 4775)
		{
			return;
		}
		if (client.getLocalPlayer() == null)
		{
			return;
		}
		final WorldPoint loc = client.getLocalPlayer().getWorldLocation();
		if (loc == null)
		{
			return;
		}
		final HerbPatches.HerbPatch p = HerbPatches.forRegion(loc.getRegionID());
		if (p == null || vb != p.varbit)
		{
			return;
		}
		final HerbPatchStatus.State newState = HerbPatches.decodeState(client.getVarbitValue(p.varbit));
		final HerbPatchStatus.State prev = lastHerbPatchState.put(p.regionId, newState);
		if (prev == HerbPatchStatus.State.DEAD
			&& newState != HerbPatchStatus.State.DEAD
			&& newState != HerbPatchStatus.State.DISEASED)
		{
			handleDeadPatchCleared(p.regionId);
		}
	}

	/**
	 * Record a cleared dead patch as part of the run, exactly like re-visiting that patch: it shows up
	 * as a (0-herb) patch row and, if its region is already in the current run, starts a new run. The
	 * restore constructor is used so no GE price/composition lookups run for the placeholder crop.
	 */
	private void handleDeadPatchCleared(int regionId)
	{
		if (!config.trackHerbs())
		{
			return;
		}
		log.debug("Dead herb patch cleared at region {} — recording as a run visit", regionId);
		submitRun(new FarmingProfitRun(Crop.UNKNOWN, 0, regionId, 0, 0, 0, LocalDateTime.now()));
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		final int id = event.getContainerId();
		if (id == InventoryID.EQUIPMENT.getId() || id == InventoryID.INVENTORY.getId())
		{
			refreshDisplay();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		// Config changes are posted on the EDT, so hop to the client thread before reading state.
		// "timetracking" fires when RuneLite writes a patch snapshot (i.e. you visit a patch), which
		// is exactly when the herb-run helper's off-region patch states change.
		if (event.getGroup().equals("farmingProfit") || event.getGroup().equals(TIMETRACKING)
			|| event.getGroup().equals(EASY_MIXOLOGY) || event.getGroup().equals(MASTERING_MIXOLOGY))
		{
			clientThread.invoke(this::refreshDisplay);
		}
	}

	// ====================== //
	//      EVENT ACTIONS     //
	// ====================== //

	/**
	 * OnGameTick event handler.
	 * The on game tick events are used to perform most harvest actions synced with the game tick timer.
	 * <p>
	 * Most of these actions use flags set in the onChatMessage since shat messages would trigger when the inventory
	 * has not updated yet. Doing it on the next game tick makes sure that the inventory is updated.
	 *
	 * @param gameTick
	 */
	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		// Throttled push-notification check (at most once every NTFY_CHECK_INTERVAL_MS). Runs here on
		// the client thread (buildHerbRunStatus reads varbits / RS-profile config) and can only fire
		// while logged in — you won't be notified if RuneLite is closed. The POST itself is async.
		maybeNotifyRunReady();

		// Set the previous crop inventory as soon as one is available.
		if (prevCropInv.size() == 0 && getCropInv().size() > 0)
		{
			prevCropInv = getCropInv();
		}

		// Distance check to make sure all runs will be added to the UI eventually.
		// getLocalPlayer() and getWorldLocation() can both be null transiently (e.g. just after a
		// world hop / region load while a run is still pending), so guard before dereferencing.
		final WorldPoint playerLoc = client.getLocalPlayer() != null
			? client.getLocalPlayer().getWorldLocation() : null;
		if (latestRun != null && playerLoc != null)
		{
			int dist = playerLoc.distanceTo2D(latestRun.getLatestHarvestWorldPoint());
			if (dist > MIN_TELEPORT_DISTANCE)
			{
				submitRun(latestRun);
				latestRun = null;
				storedObjID = -1;
			}
		}

		// Flag used to do handle starting a harvest on the next game tick
		if (START_HARVEST_NEXT_GAMETICK)
		{
			log.debug("Starting harvest");

			startedHarvesting = true;
			storedObjID = latestObjID;
			// Resolve which herb is in the patch we're standing on, for XP-based counting.
			activeHarvestCrop = harvestingHerbPatch ? resolveActiveHerb() : Crop.UNKNOWN;

			checkForHarvest();

			START_HARVEST_NEXT_GAMETICK = false;
		}

		// Flag used to handle finishing a harvest on the next game tick
		if (FINISH_HARVEST_NEXT_GAMETICK)
		{
			log.debug("Finishing harvest");

			startedHarvesting = false;

			checkForHarvest();

			submitRun(latestRun);
			latestRun = null;
			storedObjID = -1;
			activeHarvestCrop = Crop.UNKNOWN;
			harvestingHerbPatch = false;

			FINISH_HARVEST_NEXT_GAMETICK = false;
		}
	}

	/**
	 * Throttled check that fires an ntfy push when herb patches become ready. By default it sends ONE
	 * notification when the whole run transitions from not-ready to ready (the helper's rule: ready
	 * when every growing patch you planted has finished, i.e. {@code max(readyAt) <= now}); with
	 * {@code ntfyPerPatch} it instead sends one notification per patch as each becomes harvestable.
	 * Client thread only (calls buildHerbRunStatus); fires only while logged in.
	 */
	private void maybeNotifyRunReady()
	{
		final String url = config.ntfyUrl();
		if (!config.ntfyEnabled() || url == null || url.trim().isEmpty())
		{
			return;
		}
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		final long nowMs = System.currentTimeMillis();
		if (nowMs - lastNtfyCheckMs < NTFY_CHECK_INTERVAL_MS)
		{
			return;
		}
		lastNtfyCheckMs = nowMs;

		final List<HerbPatchStatus> statuses = buildHerbRunStatus();

		if (config.ntfyPerPatch())
		{
			notifyPerPatch(statuses);
			return;
		}

		// Edge-trigger the run-ready notification. The run is "ready" exactly when no patch is still
		// growing toward it but at least one patch is harvestable — mirroring HerbRunHelperPanel, and
		// robust to buildHerbRunStatus promoting past-due growing patches to READY (which would leave a
		// fully-ready run with zero "growing" patches and no ready-timestamp to compare against).
		final boolean anyGrowing = statuses.stream().anyMatch(HerbPatchStatus::countsTowardRun);
		final long readyCount = statuses.stream().filter(HerbPatchStatus::isReady).count();

		if (anyGrowing)
		{
			// Still growing — record the run's finish time so the scheduled task can fire it even if you
			// log out before it finishes, and re-arm the edge trigger.
			pendingRunReadyAt = statuses.stream()
				.filter(HerbPatchStatus::countsTowardRun)
				.mapToLong(s -> s.readyAtEpochSeconds)
				.max()
				.orElse(0);
			wasRunReady = false;
			return;
		}

		if (readyCount == 0)
		{
			// Nothing planted/harvestable — re-arm for the next run.
			pendingRunReadyAt = 0L;
			wasRunReady = false;
			return;
		}

		// Fully ready now, while logged in: fire immediately with an exact patch count.
		pendingRunReadyAt = 0L;
		if (!wasRunReady)
		{
			wasRunReady = true;
			sendRunReady(readyCount);
		}
	}

	/**
	 * Fire the whole-run-ready notification while logged OUT. {@link #onGameTick} stops firing at the
	 * login screen, so this scheduled task (which runs regardless of game state, as long as the client
	 * is open) checks the finish time {@link #pendingRunReadyAt} captured while you were logged in and
	 * sends the push once it passes. Runs off the client thread, so it must not read game state.
	 */
	@Schedule(period = 30, unit = ChronoUnit.SECONDS)
	public void scheduledRunReadyCheck()
	{
		if (!config.ntfyEnabled() || config.ntfyPerPatch())
		{
			return;
		}
		final String url = config.ntfyUrl();
		if (url == null || url.trim().isEmpty())
		{
			return;
		}
		// Only the logged-out path here; while logged in, onGameTick fires with an exact patch count.
		if (client.getGameState() == GameState.LOGGED_IN)
		{
			return;
		}
		final long target = pendingRunReadyAt;
		if (target <= 0 || wasRunReady)
		{
			return;
		}
		if (Instant.now().getEpochSecond() >= target)
		{
			wasRunReady = true;
			pendingRunReadyAt = 0L;
			sendRunReady(-1); // count unknown off the client thread
		}
	}

	/** Send the run-ready push. A non-positive count omits the patch count (logged-out path). */
	private void sendRunReady(long readyCount)
	{
		final String message = readyCount > 0
			? "Your herb run is ready — " + readyCount + " patch" + (readyCount == 1 ? "" : "es")
				+ " ready to harvest."
			: "Your herb run is ready to harvest.";
		ntfyNotifier.send(config.ntfyUrl(), "Herb run ready", message);
		log.debug("Sent ntfy herb-run-ready notification (count={})", readyCount);
	}

	/** Per-patch notifier: announce each patch as it becomes READY, once per grow cycle. */
	private void notifyPerPatch(List<HerbPatchStatus> statuses)
	{
		for (HerbPatchStatus s : statuses)
		{
			if (s.isReady())
			{
				if (notifiedReadyPatches.add(s.patchName))
				{
					final String herb = s.crop != Crop.UNKNOWN ? s.crop.getDisplayName() + " " : "";
					ntfyNotifier.send(config.ntfyUrl(), "Herb patch ready",
						herb + "at " + s.patchName + " is ready to harvest.");
				}
			}
			else if (s.state == HerbPatchStatus.State.GROWING || s.state == HerbPatchStatus.State.EMPTY)
			{
				// Patch has been (re)planted or cleared — re-arm it for the next ready transition.
				notifiedReadyPatches.remove(s.patchName);
			}
		}
	}

	/**
	 * Resolve which herb the patch the player is standing on holds, by decoding that patch's varbit.
	 * Used to attribute Farming XP gained during a harvest to the right crop. Client thread only.
	 */
	private Crop resolveActiveHerb()
	{
		if (client.getLocalPlayer() == null)
		{
			return Crop.UNKNOWN;
		}
		final WorldPoint wp = client.getLocalPlayer().getWorldLocation();
		if (wp == null)
		{
			return Crop.UNKNOWN;
		}
		final int region = wp.getRegionID();
		final HerbPatches.HerbPatch patch = HerbPatches.forRegion(region);
		if (patch == null)
		{
			return Crop.UNKNOWN;
		}
		return HerbPatches.decodeCrop(client.getVarbitValue(patch.varbit));
	}

	/**
	 * MenuOptionClicked event handler.
	 * The menu option clicked is used to track if different patches are harvested based on the latest
	 * game object clicked.
	 *
	 * @param menuOption
	 */
	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked menuOption)
	{
		if (menuOption.getMenuAction() == MenuAction.GAME_OBJECT_FIRST_OPTION &&
			client.getLocalPlayer() != null &&
			!isHarvestAnim(client.getLocalPlayer().getAnimation()))
		{
			latestObjID = menuOption.getId();
		}
	}

	/**
	 * ChatMessage event handler.
	 * The chat messages of harvesting are used to perform the harvest actions.
	 *
	 * @param chatMessage
	 */
	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		String msg = chatMessage.getMessage();

		// Harvest when chat messages are sent for individual harvests
		Crop crop = Crop.fromHarvestMessage(msg);
		if (crop != Crop.UNKNOWN)
		{
			storedObjID = latestObjID;
			handleHarvest(crop, 1);
		}

		// Since chat messages and gameticks are not synced, the inventory might not be updated when the
		// chat message is received. Flags are used handle the harvest on the next game tick.

		// Harvest Start
		if (isHarvestStartMsg(msg))
		{
			START_HARVEST_NEXT_GAMETICK = true;
			// Only the herb patch emits "You begin to harvest the herb patch."; allotments/hops/
			// flowers in the same region say "...the potatoes" etc. Gate herb XP-counting on this.
			harvestingHerbPatch = msg.startsWith("You begin to harvest the herb patch");
		}

		// Harvest Finish
		if (startedHarvesting && isHarvestFinishMsg(msg))
		{
			FINISH_HARVEST_NEXT_GAMETICK = true;
		}
	}

	// ====================== //
	//       HARVESTING       //
	// ====================== //

	/**
	 * Check inventory for a new harvest, handle harvest if new crops are detected in the inventory.
	 */
	private void checkForHarvest()
	{
		Multiset<Crop> currentCropInv = getCropInv();
		Multiset<Crop> newCrops = getNewCrops(prevCropInv, currentCropInv);
		if (newCrops.size() > 0)
		{
			Iterator<Crop> it = newCrops.iterator();
			if (it.hasNext())
			{
				Crop crop = it.next();
				// Herbs are counted from Farming XP (they may go into a herb sack instead of the
				// inventory), and crops with their own per-pick chat message (berries, seaweed,
				// cactus - see HarvestMessages) are already counted as each message arrives in
				// onChatMessage. Counting either of those again here from the inventory diff would
				// double-count: the chat-message handler updates prevCropInv from a fresh inventory
				// read that may lag a tick behind the actual game state, so the next diff check sees
				// that same pick as "new" a second time.
				if (crop.getPatchType() != PatchType.HERBS && !HarvestMessages.hasHarvestMessage(crop))
				{
					int amount = newCrops.size();
					handleHarvest(crop, amount);
				}
			}
		}
		prevCropInv = currentCropInv;
	}

	/**
	 * Handle the harvest of a crop with a given amount harvested
	 * When there is no latest run, create a new one with this crop and amount.
	 * When there is a latest run, check whether is could be the same patch. If it is the same patch, add amount to the
	 * latest run. If not, submit latest run and create a new run.
	 *
	 * @param crop   The crop that has been harvested
	 * @param amount The amount of the crop that is harvested
	 */
	private void handleHarvest(Crop crop, int amount)
	{
		// Check whether the crop patch type is enabled in the plugin config
		if (!isPatchTypeEnabled(crop.getPatchType()))
		{
			return;
		}

		log.debug("Handle harvest of " + amount + "x of " + crop.getDisplayName());

		if (client.getLocalPlayer() == null)
		{
			return;
		}
		WorldPoint harvestLocation = client.getLocalPlayer().getWorldLocation();
		if (harvestLocation == null)
		{
			return;
		}

		if (latestRun == null)
		{
			latestRun = new FarmingProfitRun(itemManager, crop, amount, harvestLocation, storedObjID);

			log.debug(" There is no latest harvest run, create new run:");
			log.debug(" " + latestRun.toString());
		}
		else
		{
			int dist = harvestLocation.distanceTo2D(latestRun.getLatestHarvestWorldPoint());
			if (latestRun.getCrop() == crop &&
				MAX_PATCH_DISTANCE > dist &&
				storedObjID == latestRun.getGameObjClicked())
			{
				latestRun.addAmount(amount);

				log.debug("  Latest run is most likely the same patch, added to latest run:");
				log.debug("  " + latestRun.toString());
			}
			else
			{
				log.debug("  Harvesting other patch type or patch far away, add latest run and start new run, because:");
				if (latestRun.getCrop() != crop)
				{
					log.debug("  Different crop type");
				}
				if (MAX_PATCH_DISTANCE <= dist)
				{
					log.debug("  Crop harvested far away");
				}
				if (storedObjID != latestRun.getGameObjClicked())
				{
					log.debug("  Different game obj clicked " + storedObjID + " != " + latestRun.getGameObjClicked());
				}

				submitRun(latestRun);
				latestRun = new FarmingProfitRun(itemManager, crop, amount, harvestLocation, storedObjID);

				log.debug(" " + latestRun.toString());
			}
		}

		prevCropInv = getCropInv();
	}

	// ====================== //
	//      UTIL METHODS      //
	// ====================== //

	private static final String RUN_HISTORY_KEY = "runHistory";
	private static final Type RUN_HISTORY_TYPE = new TypeToken<List<RunRecord>>()
	{
	}.getType();

	/** Persist the committed run history to the RS-profile config (per account). EDT-safe. */
	private void saveRuns()
	{
		configManager.setRSProfileConfiguration("farmingProfit", RUN_HISTORY_KEY,
			gson.toJson(panel.exportRecords()));
	}

	/** Load saved run history once the RS profile is available (after login). */
	private void loadRuns()
	{
		if (runsLoaded)
		{
			return;
		}
		final String json = configManager.getRSProfileConfiguration("farmingProfit", RUN_HISTORY_KEY);
		if (json == null || json.isEmpty())
		{
			return; // profile not resolved yet, or nothing saved — try again on a later refresh
		}
		runsLoaded = true;
		try
		{
			final List<RunRecord> records = gson.fromJson(json, RUN_HISTORY_TYPE);
			if (records != null)
			{
				SwingUtilities.invokeLater(() -> panel.importRecords(records));
			}
		}
		catch (Exception ex)
		{
			log.warn("Failed to parse saved farming-profit run history", ex);
		}
	}

	/** Push the in-progress patch to the panel so it shows live (counting up) while harvesting. */
	private void pushCurrentHarvest()
	{
		if (latestRun == null || !startedHarvesting)
		{
			return;
		}
		latestRun.setExpectedYield(expectedHerbYield(latestRun.getCrop()));
		final FarmingProfitRun r = latestRun;
		SwingUtilities.invokeLater(() -> panel.setCurrentHarvest(r));
	}

	private void submitRun(FarmingProfitRun run)
	{
		if (run != null)
		{
			// Compute the expected herb yield here on the client thread (reads level/compost/gear),
			// so the panel can compare actual vs expected without touching game state.
			run.setExpectedYield(expectedHerbYield(run.getCrop()));
			// Record this patch's place in the harvest route, for ordering the herb-run helper.
			if (HerbPatches.forRegion(run.getRegionId()) != null)
			{
				lastHarvestSeqByRegion.put(run.getRegionId(), ++harvestSeq);
			}
			SwingUtilities.invokeLater(() ->
			{
				log.debug("Submitting latest run " + run.toString());
				panel.addRun(run);
			});
		}
	}

	/**
	 * Expected herbs harvested from one patch of the given crop, using the player's live level,
	 * configured compost and detected gear. Returns 0 for non-herb crops. Client thread only.
	 */
	private double expectedHerbYield(Crop crop)
	{
		if (crop.getPatchType() != PatchType.HERBS)
		{
			return 0;
		}
		final PlannerInputs in = buildPlannerInputs(false);
		final int lives = in.getCompost().getHarvestLives();
		return HerbYield.expectedYieldPerPatch(crop, in.getFarmingLevel(), lives, in.itemBonusPct(), in.isAttas());
	}

	/**
	 * Get the new crops which would have to be added to prevCropInv to get the currentCropInv.
	 *
	 * @param prevCropInv
	 * @param currentCropInv
	 * @return Multiset of crops
	 */
	private static Multiset<Crop> getNewCrops(Multiset<Crop> prevCropInv, Multiset<Crop> currentCropInv)
	{
		Multiset<Crop> newItems = HashMultiset.create();

		for (Crop crop : currentCropInv.elementSet())
		{
			int count = currentCropInv.count(crop) - prevCropInv.count(crop);
			if (count > 0)
			{
				newItems.setCount(crop, count);
			}
		}

		return newItems;
	}

	/**
	 * Checks whether an animation ID is a harvest animation id
	 *
	 * @param animId Animation ID to be checked
	 * @return True if the animation ID is a harvest animation, false if not
	 */
	private boolean isHarvestAnim(int animId)
	{
		return (animId == AnimationID.FARMING_HARVEST_BUSH ||
			animId == AnimationID.FARMING_HARVEST_FLOWER ||
			animId == AnimationID.FARMING_HARVEST_FRUIT_TREE ||
			animId == AnimationID.FARMING_HARVEST_HERB ||
			animId == AnimationID.DIG);
	}

	/**
	 * Gets the crops inside the player inventory as a multiset of crops
	 *
	 * @return Multiset of crops in the inventory of the player
	 */
	private Multiset<Crop> getCropInv()
	{
		ItemContainer currentItemContainer = client.getItemContainer(InventoryID.INVENTORY);
		if (currentItemContainer != null)
		{
			return invToCropSet(currentItemContainer.getItems());
		}
		return HashMultiset.create();
	}

	/**
	 * Convert an Item array (player inventory) to a Multiset of crops which are found inside this array of items.
	 *
	 * @param inv Item array (player inventory)
	 * @return Multiset of crops from the item array
	 */
	private Multiset<Crop> invToCropSet(Item[] inv)
	{
		Multiset<Crop> map = HashMultiset.create();
		for (Item item : inv)
		{
			int unnotedItemId = itemManager.canonicalize(item.getId());
			Crop crop = Crop.fromProductId(unnotedItemId);
			if (crop != Crop.UNKNOWN)
			{
				map.add(crop, item.getQuantity());
			}
		}
		return map;
	}

	/**
	 * Checks whether the chat message is a harvest starting message.
	 *
	 * @param msg
	 * @return Whether the message is sent when starting a harvest.
	 */
	private static boolean isHarvestStartMsg(String msg)
	{
		return (msg.startsWith("You begin to harvest the"));

	}

	/**
	 * Checks whether the chat message is a harvest finishing message.
	 *
	 * @param msg
	 * @return Whether the message is sent when finishing a harvest.
	 */
	private static boolean isHarvestFinishMsg(String msg)
	{
		return (msg.endsWith("patch is now empty.") ||
			msg.endsWith("allotment is now empty."));
	}

	/**
	 * Debug log a multiset of crops
	 *
	 * @param cropInv Multiset of Crop to be logged
	 */
	private static void debugLogCropSet(Multiset<Crop> cropInv)
	{
		if (cropInv.elementSet().size() == 0)
		{
			log.debug(" < no crops in multiset >");
		}
		for (Crop crop : cropInv.elementSet())
		{
			log.debug(" " + cropInv.count(crop) + "x " + crop.getDisplayName());
		}
	}

	/**
	 * Check if patch type is enabled in plugin config.
	 *
	 * @param patchType
	 * @return Whether the patch type is enabled in the plugin config.
	 */
	private boolean isPatchTypeEnabled(PatchType patchType)
	{
		switch (patchType)
		{
			case UNKNOWN:
				log.debug("Checking unknown patch type");
				return false;
			case ALLOTMENTS:
				return config.trackAllotments();
			case HERBS:
				return config.trackHerbs();
			case HOPS:
				return config.trackHops();
			case BUSHES:
				return config.trackBushes();
			case SPECIAL:
				return config.trackSpecial();
		}
		return false;
	}

}
