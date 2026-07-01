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

import java.io.IOException;
import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Publishes a push notification to an <a href="https://ntfy.sh">ntfy</a> topic with a single
 * asynchronous HTTP POST. The user pastes a full topic URL (e.g. {@code https://ntfy.sh/my-herb-runs}
 * or a self-hosted {@code https://ntfy.example.com/topic}); the message text becomes the POST body
 * and the title is sent in the {@code Title} header. For protected topics an access token may be
 * appended to the URL after a pipe, e.g. {@code https://ntfy.sh/my-topic|tk_xxxxx}, which is sent as
 * {@code Authorization: Bearer tk_xxxxx}.
 *
 * <p>The POST is fired with {@link Call#enqueue(Callback)} so it runs on OkHttp's dispatcher thread
 * pool — never on the game/client thread or the Swing EDT. All failures are swallowed and logged so a
 * dead endpoint can never break the plugin.
 */
@Slf4j
@Singleton
class NtfyNotifier
{
	private static final MediaType TEXT_PLAIN = MediaType.parse("text/plain; charset=utf-8");

	private final OkHttpClient okHttpClient;

	@Inject
	NtfyNotifier(OkHttpClient okHttpClient)
	{
		this.okHttpClient = okHttpClient;
	}

	/**
	 * Enqueue one async POST to the configured ntfy URL. No-op when the URL is blank or unparseable.
	 *
	 * @param rawUrl  the topic URL, optionally suffixed with {@code |<token>} for a Bearer token
	 * @param title   notification title (sent as the {@code Title} header); may be null/blank
	 * @param message notification body text (the POST body)
	 */
	void send(String rawUrl, String title, String message)
	{
		if (rawUrl == null || rawUrl.trim().isEmpty())
		{
			return; // not configured — silently do nothing
		}

		// Allow "<url>|<token>" so a Bearer token for a protected topic can live alongside the URL.
		String urlPart = rawUrl.trim();
		String token = null;
		final int pipe = urlPart.indexOf('|');
		if (pipe >= 0)
		{
			token = urlPart.substring(pipe + 1).trim();
			urlPart = urlPart.substring(0, pipe).trim();
		}

		final HttpUrl url = HttpUrl.parse(urlPart);
		if (url == null)
		{
			log.warn("ntfy notifier: invalid URL '{}', skipping notification", urlPart);
			return;
		}

		final Request.Builder builder = new Request.Builder()
			.url(url)
			.post(RequestBody.create(TEXT_PLAIN, message == null ? "" : message));

		if (title != null && !title.trim().isEmpty())
		{
			builder.header("Title", title.trim());
		}
		if (token != null && !token.isEmpty())
		{
			builder.header("Authorization", "Bearer " + token);
		}

		okHttpClient.newCall(builder.build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(@Nonnull Call call, @Nonnull IOException e)
			{
				log.warn("ntfy notification failed to send", e);
			}

			@Override
			public void onResponse(@Nonnull Call call, @Nonnull Response response)
			{
				try (Response r = response)
				{
					if (!r.isSuccessful())
					{
						log.warn("ntfy notification rejected: HTTP {}", r.code());
					}
				}
			}
		});
	}
}
