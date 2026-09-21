package net.runelite.client.plugins.microbot.prometheuslogin;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicScrollBarUI;
import net.runelite.client.plugins.microbot.Microbot;

/** Prometheus-themed account list: click a card to log that account in. Must be used on the Swing thread. */
final class AccountManagerWindow extends JFrame
{
    private static final String TITLE = "Prometheus Login";

    private final LoginStore store;
    private final Consumer<LoginStore.Account> onLogin;
    private final Supplier<String> statusSource;
    private final JPanel rows = new JPanel();
    private final JLabel count = label("", 10, Color.WHITE, true);
    private final PLButton toggleForm = new PLButton("+ Add account", true);
    private final JPanel form = new JPanel();
    private final JTextField labelField = field(new JTextField());
    private final JTextField userField = field(new JTextField());
    private final JPasswordField passField = (JPasswordField) field(new JPasswordField());
    private final StatusBar statusBar = new StatusBar();
    private final Timer refresh;
    private String lastUsed = "";

    AccountManagerWindow(LoginStore store, Consumer<LoginStore.Account> onLogin, Supplier<String> statusSource)
    {
        super(TITLE);
        this.store = store;
        this.onLogin = onLogin;
        this.statusSource = statusSource;
        LoginStore.Account last = store.last();
        if (last != null) lastUsed = last.username;
        if (Logo.icon() != null) setIconImage(Logo.icon());

        JPanel content = new JPanel(new BorderLayout())
        {
            @Override protected void paintComponent(Graphics g)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(Theme.BG);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setPaint(new RadialGradientPaint(120, 60, 320, new float[] {0, 1},
                    new Color[] {new Color(134, 111, 240, 34), new Color(134, 111, 240, 0)}));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        content.setOpaque(false);

        // Header art + toolbar
        JPanel north = new JPanel();
        north.setOpaque(false);
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.add(new Header());
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.setBorder(BorderFactory.createEmptyBorder(6, 20, 10, 20));
        JPanel section = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        section.setOpaque(false);
        JLabel sectionTitle = label("ACCOUNTS", 11, Theme.PURPLE, true);
        sectionTitle.setFont(Theme.tracked(java.awt.Font.BOLD, 11f, 0.16f));
        count.setForeground(Theme.MUTED);
        section.add(sectionTitle);
        section.add(count);
        toolbar.add(section, BorderLayout.WEST);
        toggleForm.addActionListener(e -> showForm(!form.isVisible()));
        toolbar.add(toggleForm, BorderLayout.EAST);
        north.add(toolbar);
        content.add(north, BorderLayout.NORTH);

        // Account list
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.setOpaque(false);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.setBorder(BorderFactory.createEmptyBorder(0, 20, 8, 12));
        holder.add(rows, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.getVerticalScrollBar().setUI(new SlimScrollBar());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));

        buildForm();
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(scroll, BorderLayout.CENTER);
        center.add(form, BorderLayout.SOUTH);
        content.add(center, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.setBorder(BorderFactory.createEmptyBorder(4, 20, 18, 20));
        south.add(statusBar, BorderLayout.CENTER);
        content.add(south, BorderLayout.SOUTH);

        WindowChrome.install(this, content, TITLE, Logo.icon(), () -> setVisible(false));
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setMinimumSize(new Dimension(420, 540));
        setPreferredSize(new Dimension(470, 680));
        rebuild();
        showForm(store.list().isEmpty());
        pack();
        setLocationRelativeTo(null);

        statusBar.set(statusSource.get());
        refresh = new Timer(1000, e -> statusBar.set(statusSource.get()));
        refresh.start();
    }

    // ---- header -----------------------------------------------------------------------------------------------------

    private static final class Header extends JComponent
    {
        Header() { setPreferredSize(new Dimension(100, 122)); setMaximumSize(new Dimension(Integer.MAX_VALUE, 122)); }

        @Override protected void paintComponent(Graphics raw)
        {
            Graphics2D g = (Graphics2D) raw.create();
            Theme.quality(g);
            int w = getWidth(), h = getHeight();
            g.setColor(Theme.BG);
            g.fillRect(0, 0, w, h);
            Logo.paintHeader(g, 0, 0, w, h, .6f, .28);
            g.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.BG, 238), w * .85f, 0, Theme.alpha(Theme.BG, 60)));
            g.fillRect(0, 0, w, h);
            g.setPaint(new GradientPaint(0, h - 46, Theme.alpha(Theme.BG, 0), 0, h, Theme.BG));
            g.fillRect(0, h - 46, w, 46);
            g.setPaint(new GradientPaint(0, 0, new Color(190, 139, 255), w, 0, new Color(66, 221, 202)));
            g.fillRect(0, 0, w, 2);

            g.setFont(Theme.tracked(java.awt.Font.PLAIN, 10f, 0.22f));
            g.setColor(Theme.MUTED);
            g.drawString("SAVED  ·  LOCAL  ·  ONE CLICK", 20, 30);

            Logo.paintWordmark(g, 20, 40, w - 40);

            badge(g, "v" + PrometheusLoginPlugin.version, Theme.PURPLE, w - 20, 16);
            badge(g, "LOCAL ONLY", Theme.CYAN, w - 20 - 78, 16);
            g.dispose();
        }

        /** Outlined pill whose right edge is at {@code right}. */
        private static void badge(Graphics2D g, String text, Color color, int right, int y)
        {
            g.setFont(Theme.tracked(java.awt.Font.BOLD, 9.5f, 0.08f));
            FontMetrics fm = g.getFontMetrics();
            int w = fm.stringWidth(text) + 20, x = right - w;
            g.setColor(Theme.alpha(color, 26));
            g.fillRoundRect(x, y, w, 22, 8, 8);
            g.setColor(Theme.alpha(color, 170));
            g.drawRoundRect(x, y, w, 22, 8, 8);
            g.setColor(color);
            g.drawString(text, x + 10, y + 15);
        }
    }

    // ---- add form ---------------------------------------------------------------------------------------------------

    private void buildForm()
    {
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBorder(BorderFactory.createEmptyBorder(0, 20, 8, 20));

        JPanel card = new JPanel()
        {
            @Override protected void paintComponent(Graphics g)
            {
                Graphics2D g2 = (Graphics2D) g.create();
                Theme.quality(g2);
                g2.setPaint(new GradientPaint(0, 0, Theme.CARD, getWidth(), getHeight(), Theme.CARD_DEEP));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.setColor(Theme.LINE);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(6, 16, 16, 16));
        card.add(caption("NAME (OPTIONAL)"));
        card.add(labelField);
        card.add(caption("USERNAME OR EMAIL"));
        card.add(userField);

        JPanel passHead = new JPanel(new BorderLayout());
        passHead.setOpaque(false);
        passHead.setAlignmentX(LEFT_ALIGNMENT);
        JLabel passCaption = caption("PASSWORD");
        JLabel show = label("Show", 11, Theme.PURPLE, false);
        show.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        show.addMouseListener(new MouseAdapter()
        {
            @Override public void mouseClicked(MouseEvent e)
            {
                boolean hidden = passField.getEchoChar() != 0;
                passField.setEchoChar(hidden ? (char) 0 : '•');
                show.setText(hidden ? "Hide" : "Show");
            }
        });
        passHead.add(passCaption, BorderLayout.WEST);
        passHead.add(show, BorderLayout.EAST);
        passHead.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        card.add(passHead);
        card.add(passField);

        PLButton save = new PLButton("Save account", true);
        PLButton cancel = new PLButton("Cancel", false);
        save.addActionListener(e -> addAccount());
        cancel.addActionListener(e -> { clearForm(); showForm(false); });
        passField.addActionListener(e -> addAccount());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        buttons.add(cancel);
        buttons.add(save);
        card.add(buttons);
        form.add(card);
    }

    private void showForm(boolean show)
    {
        form.setVisible(show);
        toggleForm.setText(show ? "Close" : "+ Add account");
        if (show) userField.requestFocusInWindow();
        revalidate();
        repaint();
    }

    private void clearForm()
    {
        labelField.setText("");
        userField.setText("");
        passField.setText("");
    }

    // ---- account list -----------------------------------------------------------------------------------------------

    private void rebuild()
    {
        rows.removeAll();
        List<LoginStore.Account> accounts = store.list();
        count.setText(accounts.size() + (accounts.size() == 1 ? " saved" : " saved"));
        if (accounts.isEmpty())
        {
            JLabel empty = label("No accounts yet", 14, Theme.TEXT, true);
            empty.setAlignmentX(CENTER_ALIGNMENT);
            JLabel hint = label("Add one to log in with a single click.", 12, Theme.MUTED, false);
            hint.setAlignmentX(CENTER_ALIGNMENT);
            rows.add(Box.createVerticalStrut(34));
            rows.add(empty);
            rows.add(Box.createVerticalStrut(4));
            rows.add(hint);
        }
        for (LoginStore.Account a : accounts)
        {
            rows.add(new AccountCard(a));
            rows.add(Box.createVerticalStrut(9));
        }
        rows.revalidate();
        rows.repaint();
    }

    private final class AccountCard extends JPanel
    {
        private boolean hover;
        private final boolean recent;
        private final LoginStore.Account account;

        AccountCard(LoginStore.Account a)
        {
            super(new BorderLayout(13, 0));
            account = a;
            recent = a.username.equalsIgnoreCase(lastUsed);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(11, 13, 11, 13));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
            setPreferredSize(new Dimension(300, 66));
            setAlignmentX(LEFT_ALIGNMENT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText("Log in as " + a.label);

            add(new Avatar(a), BorderLayout.WEST);

            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            text.add(Box.createVerticalGlue());
            text.add(label(a.label, 14, Theme.TEXT, true));
            text.add(label(a.username, 12, Theme.MUTED, false));
            text.add(Box.createVerticalGlue());
            add(text, BorderLayout.CENTER);

            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            right.setOpaque(false);
            JLabel remove = label("×", 17, Theme.MUTED, false);
            remove.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            remove.setToolTipText("Remove this account");
            remove.setBorder(BorderFactory.createEmptyBorder(0, 6, 2, 2));
            remove.addMouseListener(new MouseAdapter()
            {
                @Override public void mouseEntered(MouseEvent e) { remove.setForeground(Theme.BAD); }
                @Override public void mouseExited(MouseEvent e) { remove.setForeground(Theme.MUTED); }
                @Override public void mouseClicked(MouseEvent e) { removeAccount(a); }
            });
            right.add(remove);
            add(right, BorderLayout.EAST);

            addMouseListener(new MouseAdapter()
            {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e)
                {
                    hover = getMousePosition(true) != null; // moving onto a child still counts as inside
                    repaint();
                }
                @Override public void mouseClicked(MouseEvent e) { login(a); }
            });
        }

        @Override protected void paintComponent(Graphics raw)
        {
            Graphics2D g = (Graphics2D) raw.create();
            Theme.quality(g);
            int w = getWidth() - 1, h = getHeight() - 1;
            g.setPaint(new GradientPaint(0, 0, hover ? Theme.CARD_HOVER : Theme.CARD, w, h, Theme.CARD_DEEP));
            g.fillRoundRect(0, 0, w, h, 14, 14);
            g.setColor(hover ? Theme.PURPLE : Theme.LINE);
            g.drawRoundRect(0, 0, w, h, 14, 14);
            if (recent)
            {
                g.setFont(Theme.tracked(java.awt.Font.BOLD, 9f, 0.08f));
                FontMetrics fm = g.getFontMetrics();
                String t = "LAST USED";
                int pw = fm.stringWidth(t) + 16, px = w - 44 - pw, py = (h - 20) / 2;
                g.setColor(Theme.alpha(Theme.CYAN, 28));
                g.fillRoundRect(px, py, pw, 20, 8, 8);
                g.setColor(Theme.alpha(Theme.CYAN, 160));
                g.drawRoundRect(px, py, pw, 20, 8, 8);
                g.setColor(Theme.CYAN);
                g.drawString(t, px + 8, py + 14);
            }
            g.dispose();
        }
    }

    /** Round badge with the account's initial, in a purple-to-blue shade derived from the username. */
    private static final class Avatar extends JComponent
    {
        private final Color from;
        private final Color to;
        private final String initial;

        Avatar(LoginStore.Account a)
        {
            float hue = .60f + (Math.abs(a.username.toLowerCase().hashCode()) % 200) / 1000f; // blue .60 -> violet .80
            from = Color.getHSBColor(hue, .42f, 1f);
            to = Color.getHSBColor(hue - .08f, .70f, .78f);
            initial = a.label.isEmpty() ? "?" : a.label.substring(0, 1).toUpperCase();
            setPreferredSize(new Dimension(40, 40));
        }

        @Override protected void paintComponent(Graphics raw)
        {
            Graphics2D g = (Graphics2D) raw.create();
            Theme.quality(g);
            int d = Math.min(getWidth(), getHeight()), y = (getHeight() - d) / 2;
            g.setPaint(new GradientPaint(0, y, from, d, y + d, to));
            g.fillOval(0, y, d, d);
            g.setColor(new Color(255, 255, 255, 60));
            g.drawOval(0, y, d - 1, d - 1);
            g.setColor(Theme.ON_ACCENT);
            g.setFont(Theme.font(java.awt.Font.BOLD, 17f));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(initial, (d - fm.stringWidth(initial)) / 2, y + (d + fm.getAscent() - fm.getDescent()) / 2);
            g.dispose();
        }
    }

    // ---- status bar -------------------------------------------------------------------------------------------------

    private static final class StatusBar extends JComponent
    {
        private String text = "Ready when you are. Accounts are saved on this PC only.";
        private Color dot = Theme.MUTED;

        StatusBar() { setPreferredSize(new Dimension(100, 46)); }

        void set(String status)
        {
            String t = status == null || status.isBlank() ? "Ready when you are. Accounts are saved on this PC only." : status;
            String l = t.toLowerCase();
            Color c = Theme.MUTED;
            if (l.contains("gave up") || l.contains("banned") || l.contains("could not")) c = Theme.BAD;
            else if (l.startsWith("logged in")) c = Theme.CYAN;
            else if (l.startsWith("logging in") || l.startsWith("waiting")) c = Theme.PURPLE;
            if (!t.equals(text) || !c.equals(dot))
            {
                text = t;
                dot = c;
                repaint();
            }
        }

        @Override protected void paintComponent(Graphics raw)
        {
            Graphics2D g = (Graphics2D) raw.create();
            Theme.quality(g);
            int w = getWidth() - 1, h = getHeight() - 1;
            g.setPaint(new GradientPaint(0, 0, Theme.CARD, w, h, Theme.CARD_DEEP));
            g.fillRoundRect(0, 0, w, h, 12, 12);
            g.setColor(Theme.LINE);
            g.drawRoundRect(0, 0, w, h, 12, 12);
            g.setColor(Theme.alpha(dot, 60));
            g.fillOval(14, h / 2 - 7, 14, 14);
            g.setColor(dot);
            g.fillOval(18, h / 2 - 3, 6, 6);
            g.setFont(Theme.font(java.awt.Font.PLAIN, 12f));
            g.setColor(Theme.TEXT);
            FontMetrics fm = g.getFontMetrics();
            String shown = text;
            int max = w - 46;
            while (fm.stringWidth(shown) > max && shown.length() > 4) shown = shown.substring(0, shown.length() - 2) + "…";
            g.drawString(shown, 38, h / 2 + (fm.getAscent() - fm.getDescent()) / 2);
            g.dispose();
        }
    }

    private static final class SlimScrollBar extends BasicScrollBarUI
    {
        @Override protected void configureScrollBarColors() { thumbColor = Theme.LINE; trackColor = Theme.BG; }
        @Override protected javax.swing.JButton createDecreaseButton(int o) { return zero(); }
        @Override protected javax.swing.JButton createIncreaseButton(int o) { return zero(); }

        private static javax.swing.JButton zero()
        {
            javax.swing.JButton b = new javax.swing.JButton();
            b.setPreferredSize(new Dimension(0, 0));
            return b;
        }

        @Override protected void paintTrack(Graphics g, JComponent c, java.awt.Rectangle r) { }

        @Override protected void paintThumb(Graphics g, JComponent c, java.awt.Rectangle r)
        {
            if (r.isEmpty() || !((JScrollBar) c).isEnabled()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            Theme.quality(g2);
            g2.setColor(isThumbRollover() ? Theme.PURPLE : Theme.LINE);
            g2.fillRoundRect(r.x + 1, r.y, r.width - 2, r.height, 6, 6);
            g2.dispose();
        }
    }

    // ---- actions ----------------------------------------------------------------------------------------------------

    private void login(LoginStore.Account a)
    {
        if (Microbot.isLoggedIn())
        {
            JOptionPane.showMessageDialog(this, "A character is already logged in.\nLog out first, then click the account.", TITLE, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        lastUsed = a.username;
        try { store.setLast(a.username); }
        catch (Exception ex) { statusBar.set("Could not save last used account: " + ex.getMessage()); return; }
        onLogin.accept(a);
        rebuild();
        statusBar.set(statusSource.get());
    }

    private void addAccount()
    {
        String user = userField.getText().trim();
        String pass = new String(passField.getPassword());
        if (user.isEmpty() || pass.isEmpty())
        {
            statusBar.set("Could not save: enter both a username and a password");
            return;
        }
        try
        {
            boolean existed = store.find(user) != null;
            store.put(labelField.getText().trim(), user, pass);
            statusBar.set((existed ? "Updated " : "Saved ") + user);
        }
        catch (Exception ex)
        {
            statusBar.set("Could not save account: " + ex.getMessage());
            return;
        }
        clearForm();
        showForm(false);
        rebuild();
    }

    private void removeAccount(LoginStore.Account a)
    {
        int ok = JOptionPane.showConfirmDialog(this, "Remove saved account \"" + a.label + "\"?", TITLE, JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;
        try { store.remove(a.username); }
        catch (Exception ex) { statusBar.set("Could not remove account: " + ex.getMessage()); return; }
        if (a.username.equalsIgnoreCase(lastUsed)) lastUsed = "";
        rebuild();
    }

    // ---- helpers ----------------------------------------------------------------------------------------------------

    private static JLabel label(String text, float size, Color color, boolean bold)
    {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(bold ? java.awt.Font.BOLD : java.awt.Font.PLAIN, size));
        l.setForeground(color);
        return l;
    }

    private static JLabel caption(String text)
    {
        JLabel l = new JLabel(text);
        l.setFont(Theme.tracked(java.awt.Font.BOLD, 9.5f, 0.12f));
        l.setForeground(Theme.MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(12, 0, 6, 0));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private static JTextField field(JTextField f)
    {
        Color idle = Theme.LINE;
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(idle), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        f.setBackground(Theme.FIELD);
        f.setForeground(Theme.TEXT);
        f.setCaretColor(Theme.PURPLE);
        f.setSelectionColor(Theme.alpha(Theme.PURPLE, 110));
        f.setFont(Theme.font(java.awt.Font.PLAIN, 13f));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        f.setAlignmentX(LEFT_ALIGNMENT);
        f.addFocusListener(new FocusAdapter()
        {
            @Override public void focusGained(FocusEvent e) { setBorderColor(f, Theme.PURPLE); }
            @Override public void focusLost(FocusEvent e) { setBorderColor(f, idle); }
        });
        return f;
    }

    private static void setBorderColor(JTextField f, Color c)
    {
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(c), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
    }

    @Override public void dispose()
    {
        refresh.stop();
        super.dispose();
    }
}
