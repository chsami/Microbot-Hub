package net.runelite.client.plugins.microbot.prometheuslogin;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import net.runelite.client.RuneLite;

/**
 * Saved accounts, kept on this machine only in {user}/.runelite/prometheuslogin/accounts.properties. Passwords are
 * AES-GCM encrypted with a key derived from this machine's user/host name: that stops casual reading or copying of the
 * file to another machine, but it is obfuscation, not protection against malware running as you. Nothing is ever
 * written into the plugin, its config, or anywhere under a source tree.
 */
final class LoginStore
{
    static final class Account
    {
        final String label;
        final String username;
        final String password;

        Account(String label, String username, String password)
        {
            this.username = username;
            this.label = label == null || label.isBlank() ? username : label;
            this.password = password;
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SEED_TAG = "prometheuslogin";
    private static final String LEGACY_SEED_TAG = "credentiallogin"; // earlier development name of this plugin

    private final Path dir = RuneLite.RUNELITE_DIR.toPath().resolve("prometheuslogin");
    private final Path file = dir.resolve("accounts.properties");
    private final Path legacyFile = RuneLite.RUNELITE_DIR.toPath().resolve("credentiallogin").resolve("accounts.properties");
    private final List<Account> accounts = new ArrayList<>();
    private String last = "";

    synchronized void load()
    {
        accounts.clear();
        last = "";
        try
        {
            if (Files.isRegularFile(file))
            {
                read(file, SEED_TAG);
            }
            else if (Files.isRegularFile(legacyFile))
            {
                read(legacyFile, LEGACY_SEED_TAG);
                if (!accounts.isEmpty())
                {
                    save();
                    Files.deleteIfExists(legacyFile);
                }
            }
        }
        catch (Exception ignored) { /* unreadable: start empty */ }
    }

    private void read(Path path, String seedTag) throws Exception
    {
        Properties p = new Properties();
        try (var in = Files.newInputStream(path)) { p.load(in); }
        last = p.getProperty("last", "");
        for (int i = 0; ; i++)
        {
            String user = p.getProperty("account." + i + ".username");
            if (user == null) break;
            try { accounts.add(new Account(p.getProperty("account." + i + ".label"), user, decrypt(p.getProperty("account." + i + ".password", ""), seedTag))); }
            catch (Exception skip) { /* undecryptable (copied from another machine): skip this entry */ }
        }
    }

    synchronized List<Account> list() { return new ArrayList<>(accounts); }

    synchronized Account find(String username)
    {
        for (Account a : accounts) if (a.username.equalsIgnoreCase(username)) return a;
        return null;
    }

    synchronized Account last() { return last.isEmpty() ? null : find(last); }

    /** Adds the account, or replaces the saved one with the same username. */
    synchronized void put(String label, String username, String password) throws Exception
    {
        accounts.removeIf(a -> a.username.equalsIgnoreCase(username));
        accounts.add(new Account(label, username, password));
        save();
    }

    synchronized void remove(String username) throws Exception
    {
        accounts.removeIf(a -> a.username.equalsIgnoreCase(username));
        if (last.equalsIgnoreCase(username)) last = "";
        save();
    }

    synchronized void setLast(String username) throws Exception
    {
        last = username;
        save();
    }

    private void save() throws Exception
    {
        Files.createDirectories(dir);
        Properties p = new Properties();
        p.setProperty("last", last);
        for (int i = 0; i < accounts.size(); i++)
        {
            Account a = accounts.get(i);
            p.setProperty("account." + i + ".label", a.label);
            p.setProperty("account." + i + ".username", a.username);
            p.setProperty("account." + i + ".password", encrypt(a.password));
        }
        Path tmp = file.resolveSibling("accounts.properties.tmp");
        try (var out = Files.newOutputStream(tmp)) { p.store(out, "Prometheus Login - private, do not share"); }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    private static SecretKeySpec key(String seedTag) throws Exception
    {
        String seed = System.getProperty("user.name", "") + "|" + System.getenv().getOrDefault("COMPUTERNAME", "") + "|" + seedTag;
        return new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8)), "AES");
    }

    private static String encrypt(String plain) throws Exception
    {
        byte[] iv = new byte[12];
        RANDOM.nextBytes(iv);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key(SEED_TAG), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        byte[] all = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, all, 0, iv.length);
        System.arraycopy(ct, 0, all, iv.length, ct.length);
        return Base64.getEncoder().encodeToString(all);
    }

    private static String decrypt(String stored, String seedTag) throws Exception
    {
        byte[] all = Base64.getDecoder().decode(stored);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(seedTag), new GCMParameterSpec(128, all, 0, 12));
        return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
    }
}
