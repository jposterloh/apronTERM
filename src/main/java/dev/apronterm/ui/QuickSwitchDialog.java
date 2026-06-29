package dev.apronterm.ui;

import dev.apronterm.app.I18n;
import dev.apronterm.project.Project;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * Keyboard-first quick switcher: type to filter projects, ↑/↓ to move, Enter (or double-click) to
 * switch, Esc to cancel. Projects with running tabs are highlighted and show their tab count.
 * (Issue #9)
 */
public final class QuickSwitchDialog extends JDialog {

    private final List<Project> all;
    private final String activeName;
    private final ToIntFunction<Project> liveCount;
    private final Consumer<Project> onChoose;

    private final JTextField search = new JTextField();
    private final DefaultListModel<Project> model = new DefaultListModel<>();
    private final JList<Project> list = new JList<>(model);

    public QuickSwitchDialog(Frame owner, List<Project> projects, String activeName,
                             ToIntFunction<Project> liveCount, Consumer<Project> onChoose) {
        super(owner, I18n.t("quickSwitch.title"), true);
        this.all = new ArrayList<>(projects);
        this.activeName = activeName;
        this.liveCount = liveCount;
        this.onChoose = onChoose;

        buildUi();
        refilter("");
        selectActiveOrFirst();

        setSize(420, 320);
        setLocationRelativeTo(owner);
    }

    private void buildUi() {
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        search.putClientProperty("JTextField.placeholderText", I18n.t("quickSwitch.searchPlaceholder"));
        root.add(search, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        root.add(new JScrollPane(list), BorderLayout.CENTER);

        setContentPane(root);

        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) {
                refilter(search.getText());
            }

            @Override public void removeUpdate(DocumentEvent e) {
                refilter(search.getText());
            }

            @Override public void changedUpdate(DocumentEvent e) {
                refilter(search.getText());
            }
        });

        // Arrows move the list selection while focus stays in the search field; Enter confirms.
        search.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN -> {
                        move(1);
                        e.consume();
                    }
                    case KeyEvent.VK_UP -> {
                        move(-1);
                        e.consume();
                    }
                    case KeyEvent.VK_ENTER -> {
                        confirm();
                        e.consume();
                    }
                    default -> {
                    }
                }
            }
        });

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int i = list.locationToIndex(e.getPoint());
                    if (i >= 0) {
                        list.setSelectedIndex(i);
                        confirm();
                    }
                }
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) {
                search.requestFocusInWindow();
            }
        });
    }

    private void move(int delta) {
        int n = model.getSize();
        if (n == 0) {
            return;
        }
        int i = Math.floorMod(list.getSelectedIndex() + delta, n);
        list.setSelectedIndex(i);
        list.ensureIndexIsVisible(i);
    }

    private void confirm() {
        Project p = list.getSelectedValue();
        if (p != null) {
            onChoose.accept(p);
            dispose();
        }
    }

    private void refilter(String text) {
        String q = text.trim().toLowerCase(Locale.ROOT);
        Project prev = list.getSelectedValue();
        model.clear();
        for (Project p : all) {
            if (q.isEmpty() || p.name.toLowerCase(Locale.ROOT).contains(q)) {
                model.addElement(p);
            }
        }
        if (prev != null && model.contains(prev)) {
            list.setSelectedValue(prev, true);
        } else if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    private void selectActiveOrFirst() {
        for (int i = 0; i < model.getSize(); i++) {
            if (model.get(i).name.equals(activeName)) {
                list.setSelectedIndex(i);
                list.ensureIndexIsVisible(i);
                return;
            }
        }
        if (!model.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    /** Bold + tab count for projects with running tabs; a ▶ marker for the active one. */
    private final class Renderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> l, Object value, int index,
                                                      boolean selected, boolean focus) {
            super.getListCellRendererComponent(l, value, index, selected, focus);
            Project p = (Project) value;
            int count = liveCount.applyAsInt(p);
            StringBuilder sb = new StringBuilder();
            if (p.name.equals(activeName)) {
                sb.append("▶ ");
            }
            sb.append(p.name);
            if (count > 0) {
                sb.append("   ● ").append(I18n.t(count == 1 ? "quickSwitch.tabCount.one" : "quickSwitch.tabCount.other", count));
            }
            setText(sb.toString());
            Font base = l.getFont();
            setFont(count > 0 ? base.deriveFont(Font.BOLD) : base);
            setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            return this;
        }
    }
}
