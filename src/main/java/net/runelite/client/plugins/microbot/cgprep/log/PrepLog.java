package net.runelite.client.plugins.microbot.cgprep.log;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CG Prep's decision log: what the bot saw, decided and did, one line per event, in the same
 * "seconds tTICK TYPE body" format as CG Recon so the two files line up by game tick.
 *
 * Written next to the CG Recon logs (it reuses CG Recon's output folder), file name
 * cg-prep-yyyyMMdd-HHmmss.log. All file I/O is on one background thread.
 */
public final class PrepLog
{
	private static final Logger log = LoggerFactory.getLogger(PrepLog.class);

	private final ExecutorService io = Executors.newSingleThreadExecutor(r ->
	{
		Thread t = new Thread(r, "cg-prep-log");
		t.setDaemon(true);
		return t;
	});
	private final long startMs = System.currentTimeMillis();
	private final IntSupplier tick;
	private volatile BufferedWriter out;
	private volatile File file;
	private volatile long lastFlushMs;
	/** Lines queued but not yet written; the writer flushes when it reaches 0 (nothing sits in the buffer). */
	private final java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger();

	public PrepLog(IntSupplier tick)
	{
		this.tick = tick;
	}

	public void open(File dir) throws IOException
	{
		Files.createDirectories(dir.toPath());
		String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
		file = new File(dir, "cg-prep-" + stamp + ".log");
		out = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8);
		prune(dir);
	}

	/** Files kept in the log folder (newest first by name = date); older ones are deleted at start. */
	public static final int KEEP_FILES = 20;

	private void prune(File dir)
	{
		File[] all = dir.listFiles((d, n) -> n.startsWith("cg-prep-") && n.endsWith(".log"));
		if (all == null || all.length <= KEEP_FILES)
		{
			return;
		}
		java.util.Arrays.sort(all, (a, b) -> b.getName().compareTo(a.getName()));
		for (int i = KEEP_FILES; i < all.length; i++)
		{
			if (!all[i].equals(file) && !all[i].delete())
			{
				log.debug("could not delete old decision log {}", all[i]);
			}
		}
	}

	public File getFile()
	{
		return file;
	}

	public void line(String type, String body)
	{
		if (out == null || io.isShutdown())
		{
			return;
		}
		double secs = (System.currentTimeMillis() - startMs) / 1000.0;
		int t;
		try
		{
			t = tick.getAsInt();
		}
		catch (Exception e)
		{
			t = -1;
		}
		String s = String.format("%08.2f t%d %s %s", secs, t, type, body);
		pending.incrementAndGet();
		try
		{
			io.execute(() -> write(s));
		}
		catch (java.util.concurrent.RejectedExecutionException e)
		{
			pending.decrementAndGet(); // closing: the line is dropped
		}
	}

	public void error(String where, Throwable ex)
	{
		StringBuilder sb = new StringBuilder(where).append(": ").append(ex);
		StackTraceElement[] st = ex.getStackTrace();
		for (int i = 0; i < Math.min(6, st.length); i++)
		{
			sb.append(" | at ").append(st[i]);
		}
		line("ERR", sb.toString());
	}

	public void close()
	{
		io.execute(() ->
		{
			try
			{
				if (out != null)
				{
					out.flush();
					out.close();
				}
			}
			catch (IOException e)
			{
				log.warn("CG Prep: failed to close log", e);
			}
			out = null;
		});
		io.shutdown();
		try
		{
			io.awaitTermination(3, TimeUnit.SECONDS);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
	}

	private void write(String s)
	{
		BufferedWriter w = out;
		if (w == null)
		{
			return;
		}
		try
		{
			w.write(s);
			w.newLine();
			long now = System.currentTimeMillis();
			if (pending.decrementAndGet() <= 0 || now - lastFlushMs > 1000)
			{
				w.flush();
				lastFlushMs = now;
			}
		}
		catch (IOException e)
		{
			log.warn("CG Prep: log write failed", e);
		}
	}
}
