package dev.apronterm.ui;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntConsumer;
import javax.swing.JLabel;

/** A tab header with a title label and a small close (×) button. */
public final class ButtonTabComponent extends JPanel {

    /** Accent for an unseen bell notification; readable on both dark and light chrome. (#12) */
    private static final Color NOTIFY_COLOR = new Color(0xFF, 0x98, 0x00);

    private final JLabel label;
    private boolean notified;

    public ButtonTabComponent(JTabbedPane pane, IntConsumer onClose) {
        super(new FlowLayout(FlowLayout.LEFT, 0, 0));
        setOpaque(false);

        label = new JLabel() {
            @Override
            public String getText() {
                int i = pane.indexOfTabComponent(ButtonTabComponent.this);
                String title = i != -1 ? pane.getTitleAt(i) : "";
                return notified ? "● " + title : title;
            }
        };
        label.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 0, 2, 6));
        add(label);
        add(new CloseButton(pane, onClose));
    }

    /** Mark/unmark this tab as having an unseen bell notification. (#12) */
    public void setNotified(boolean notified) {
        if (this.notified == notified) {
            return;
        }
        this.notified = notified;
        label.setForeground(notified ? NOTIFY_COLOR : null); // null = inherit default
        label.setFont(label.getFont().deriveFont(notified ? Font.BOLD : Font.PLAIN));
        revalidate();
        repaint();
    }

    private static final class CloseButton extends JButton {

        private CloseButton(JTabbedPane pane, IntConsumer onClose) {
            int size = 16;
            setPreferredSize(new Dimension(size, size));
            setToolTipText(dev.apronterm.app.I18n.t("tab.close"));
            setUI(new BasicButtonUI());
            setContentAreaFilled(false);
            setFocusable(false);
            setBorderPainted(false);
            setRolloverEnabled(true);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    setBorderPainted(true);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setBorderPainted(false);
                }
            });
            ActionListener l = e -> {
                int i = pane.indexOfTabComponent(getParent());
                if (i != -1) {
                    onClose.accept(i);
                }
            };
            addActionListener(l);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            if (getModel().isRollover()) {
                g2.setColor(new Color(220, 80, 80));
            } else {
                g2.setColor(Color.GRAY);
            }
            g2.setStroke(new BasicStroke(1.5f));
            int d = 5;
            int x = getWidth() / 2;
            int y = getHeight() / 2;
            g2.drawLine(x - d, y - d, x + d, y + d);
            g2.drawLine(x - d, y + d, x + d, y - d);
            g2.dispose();
        }
    }
}
