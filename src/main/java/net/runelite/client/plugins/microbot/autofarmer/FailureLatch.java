package net.runelite.client.plugins.microbot.autofarmer;
import java.nio.file.*;
import java.io.*;
import java.util.Properties;
/** A failure survives client restarts and requires an explicit user reset. */
final class FailureLatch {
    private final Path file;
    private final Properties state = new Properties();
    FailureLatch(Path file) {
        this.file=file;
        if (Files.exists(file)) try (InputStream in=Files.newInputStream(file)) { state.load(in); }
        catch (Exception e) { state.setProperty("active","true");state.setProperty("reason","Could not read failure report: "+e.getClass().getSimpleName()); }
    }
    synchronized boolean active() { return Boolean.parseBoolean(state.getProperty("active","false")); }
    synchronized String reason() { return state.getProperty("reason",""); }
    synchronized void trip(String reason,String patch,long now) throws IOException {
        if(active())return;
        state.setProperty("active","true");state.setProperty("reason",reason);
        state.setProperty("patch",patch==null?"none":patch);state.setProperty("timeUtc",java.time.Instant.ofEpochMilli(now).toString());
        state.setProperty("version",AutoFarmerPlugin.version);persist();
    }
    synchronized void clear() throws IOException {state.setProperty("active","false");state.setProperty("clearedAtUtc",java.time.Instant.now().toString());persist();}
    private void persist() throws IOException {
        Files.createDirectories(file.getParent());Path tmp=file.resolveSibling(file.getFileName()+".tmp");
        try(OutputStream out=Files.newOutputStream(tmp)){state.store(out,"Auto Farmer failure report; no credentials");}
        Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);
    }
}
