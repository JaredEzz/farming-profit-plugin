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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import javax.inject.Singleton;
import org.slf4j.LoggerFactory;

/**
 * Captures this plugin's own log output (every existing {@code log.debug}/{@code warn}/{@code error}
 * call already scattered through {@link FarmingProfitPlugin}, including any logged exception) into a
 * bounded in-memory ring buffer. Lets a playtester without a Java console (e.g. on a Steam Deck) copy
 * useful diagnostics straight into a bug report via the Tracker tab's "Copy debug log" menu item,
 * instead of the maintainer needing a live debug session.
 *
 * <p>Off by default: {@link #setEnabled} attaches/detaches this appender to the
 * {@code com.farmingprofit} logger and raises/restores its level, so no extra console output or
 * memory use happens unless a user opts in via config.
 */
@Singleton
class DebugLog extends AppenderBase<ILoggingEvent>
{
	private static final int MAX_ENTRIES = 500;
	private static final String LOGGER_NAME = "com.farmingprofit";
	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

	private final Deque<String> entries = new ArrayDeque<>();
	private boolean attached = false;

	synchronized void setEnabled(boolean enabled)
	{
		if (enabled == attached)
		{
			return;
		}
		final Logger logger = (Logger) LoggerFactory.getLogger(LOGGER_NAME);
		if (enabled)
		{
			setContext(logger.getLoggerContext());
			start();
			logger.addAppender(this);
			logger.setLevel(Level.DEBUG);
		}
		else
		{
			logger.detachAppender(this);
			logger.setLevel(null); // back to whatever the root/config level is
			stop();
			entries.clear();
		}
		attached = enabled;
	}

	@Override
	protected synchronized void append(ILoggingEvent event)
	{
		final StringBuilder sb = new StringBuilder()
			.append(LocalDateTime.now().format(TIME_FORMAT))
			.append(' ').append(event.getLevel())
			.append(' ').append(event.getFormattedMessage());
		final IThrowableProxy throwable = event.getThrowableProxy();
		if (throwable != null)
		{
			sb.append('\n').append(ThrowableProxyUtil.asString(throwable));
		}
		entries.addLast(sb.toString());
		while (entries.size() > MAX_ENTRIES)
		{
			entries.removeFirst();
		}
	}

	/** Newest-last, one line per log event. Empty when disabled or nothing has been logged yet. */
	synchronized String snapshot()
	{
		return String.join("\n", entries);
	}
}
