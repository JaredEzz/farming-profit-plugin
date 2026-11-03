package com.farmingprofit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the bottomless compost bucket's remaining uses from game text. The carried bucket's count
 * isn't readable from a varbit (varbit 7916 only holds it while the bucket is stored at a Tool
 * Leprechaun), so game text is the only source for it.
 */
final class CompostBucketMessages
{
	/** Returned when the text says nothing about how many uses the bucket holds. */
	static final int NO_COUNT = -1;

	private static final int FULL_USES = 10000;
	private static final Pattern FILL_TOTAL = Pattern.compile("now contains a total of (\\d+) uses?");
	private static final Pattern USES = Pattern.compile("(?:is currently holding|has) (one|a single|\\d+) uses? of");
	private static final Pattern TREATED = Pattern.compile("^You treat the .+ with (?:ultra|super|)compost\\.$");

	private CompostBucketMessages()
	{
	}

	/**
	 * @param message chat or dialog text, which may contain tags, line breaks and thousands separators
	 * @return the uses this text reports, or {@link #NO_COUNT} if it doesn't report any
	 */
	static int usesFrom(String message)
	{
		final String text = clean(message);
		if (!text.contains("compost bucket"))
		{
			return NO_COUNT;
		}
		if (text.startsWith("You discard") || text.contains("run out")
			|| text.contains("currently empty") || text.contains("doesn't currently have any compost"))
		{
			return 0;
		}
		if (text.contains("is now full"))
		{
			return FULL_USES;
		}
		final Matcher total = FILL_TOTAL.matcher(text);
		if (total.find())
		{
			return Integer.parseInt(total.group(1));
		}
		final Matcher uses = USES.matcher(text);
		if (uses.find())
		{
			final String count = uses.group(1);
			return count.equals("one") || count.equals("a single") ? 1 : Integer.parseInt(count);
		}
		return NO_COUNT;
	}

	/** Whether this text is the game saying compost was just applied to a patch. */
	static boolean isCompostApplied(String message)
	{
		return TREATED.matcher(clean(message)).matches();
	}

	private static String clean(String message)
	{
		return message.replace("<br>", " ").replaceAll("<[^>]*>", "").replace(",", "").trim();
	}
}
