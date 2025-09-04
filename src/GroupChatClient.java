// filename:GroupChatClient.java
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.security.SecureRandom;

public class GroupChatClient {
    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;
    private JTextPane chatPane;
    private JTextField inputField;
    private String userName;

    // AES (must match server)
    private static final byte[] AES_KEY = "0123456789ABCDEF".getBytes();
    private static final SecureRandom RANDOM = new SecureRandom();

    // Downloads folder fixed (Issue 2)
    private static final String DOWNLOADS_BASE = "DRDO_Internship/ChatServer_more/src/downloads";

    // Online users list
    private DefaultListModel<String> usersModel = new DefaultListModel<>();
    private JList<String> usersList = new JList<>(usersModel);

    // Settings state
    private boolean notificationsEnabled = true;
    private boolean darkTheme = true; // default unchanged

    // UI references for dynamic font scaling
    private JFrame mainFrame;
    private int baseChatFontSize = 15;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GroupChatClient().showLogin());
    }

    private void showLogin() {
        JDialog loginDialog = new JDialog((Frame) null, "Welcome to ChatX 🎉", true);
        loginDialog.setSize(420, 220); // slightly bigger as requested
        loginDialog.setLocationRelativeTo(null);
        loginDialog.setLayout(new BorderLayout(12, 12));

        JLabel welcome = new JLabel("✨ Welcome to ChatX ✨", JLabel.CENTER);
        welcome.setFont(new Font("Segoe UI", Font.BOLD, 22)); // better font and size
        loginDialog.add(welcome, BorderLayout.NORTH);

        JPanel panel = new JPanel(new GridLayout(2, 1, 6, 6));
        JLabel label = new JLabel("Enter Username:");
        label.setFont(new Font("Segoe UI", Font.BOLD, 16)); // better font and size
        JTextField nameField = new JTextField();
        nameField.setFont(new Font("Segoe UI", Font.PLAIN, 16)); // 🔥 increase font size here
        panel.add(label);
        panel.add(nameField);
        loginDialog.add(panel, BorderLayout.CENTER);

        JButton joinButton = new JButton("Join Chat");
        joinButton.setFont(new Font("Segoe UI", Font.BOLD, 16)); // better font and size
        joinButton.setBackground(new Color(0, 120, 215));
        joinButton.setForeground(Color.WHITE);
        joinButton.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(loginDialog, "Name cannot be empty.", "Error", JOptionPane.ERROR_MESSAGE);
            } else {
                userName = name;
                loginDialog.dispose();
                createGUI();
            }
        });
        loginDialog.add(joinButton, BorderLayout.SOUTH);

        loginDialog.setVisible(true);
    }

    private void createGUI() {
        mainFrame = new JFrame("ChatX - " + userName);
        mainFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        mainFrame.setSize(800, 550);
        mainFrame.setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel() {
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;
                GradientPaint gp = new GradientPaint(0, 0, new Color(10, 25, 47),
                        0, getHeight(), new Color(25, 90, 130));
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        mainPanel.setLayout(new BorderLayout());

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT));
        header.setOpaque(false);

        JLabel title = new JLabel("ChatX - Logged in as: " + userName);
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Color.WHITE);
        header.add(title);

        JButton newClientBtn = new JButton("New Client");
        newClientBtn.setBackground(new Color(30, 150, 200));
        newClientBtn.setForeground(Color.WHITE);
        newClientBtn.setFocusPainted(false);
        newClientBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        newClientBtn.addActionListener(e -> new Thread(() -> new GroupChatClient().showLogin()).start());
        header.add(Box.createHorizontalStrut(20));
        header.add(newClientBtn);

        // ADDED: Settings and About buttons (keeps style consistent)
        JButton settingsBtn = new JButton("Settings");
        settingsBtn.setBackground(new Color(30, 150, 200));
        settingsBtn.setForeground(Color.WHITE);
        settingsBtn.setFocusPainted(false);
        settingsBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        settingsBtn.addActionListener(e -> showSettingsDialog());

        JButton aboutBtn = new JButton("About");
        aboutBtn.setBackground(new Color(30, 150, 200));
        aboutBtn.setForeground(Color.WHITE);
        aboutBtn.setFocusPainted(false);
        aboutBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        aboutBtn.addActionListener(e -> showAboutDialog());

        header.add(settingsBtn);
        header.add(Box.createHorizontalStrut(8));
        header.add(aboutBtn);

        mainPanel.add(header, BorderLayout.NORTH);

        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setBackground(new Color(15, 35, 65));
        chatPane.setForeground(Color.WHITE);
        chatPane.setFont(new Font("Segoe UI", Font.PLAIN, baseChatFontSize));

        JScrollPane chatScroll = new JScrollPane(chatPane);
        chatScroll.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        usersList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        usersList.setBackground(new Color(20, 40, 70));
        usersList.setForeground(Color.WHITE);
        usersList.setFixedCellWidth(140);
        JScrollPane usersScroll = new JScrollPane(usersList);
        usersScroll.setBorder(BorderFactory.createTitledBorder("Online Users"));

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(chatScroll, BorderLayout.CENTER);
        centerPanel.add(usersScroll, BorderLayout.EAST);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        bottomPanel.setOpaque(false);

        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputField.setBackground(new Color(30, 50, 80));
        inputField.setForeground(Color.WHITE);
        inputField.setCaretColor(Color.WHITE);
        inputField.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JButton sendButton = new JButton("Send");
        sendButton.setFocusPainted(false);
        sendButton.setBackground(new Color(0, 120, 215));
        sendButton.setForeground(Color.WHITE);
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sendButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JButton fileButton = new JButton("📁 Send File");
        fileButton.setFocusPainted(false);
        fileButton.setBackground(new Color(0, 170, 120));
        fileButton.setForeground(Color.WHITE);
        fileButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        fileButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JPanel btnPanel = new JPanel();
        btnPanel.setOpaque(false);
        btnPanel.add(sendButton);
        btnPanel.add(fileButton);

        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(btnPanel, BorderLayout.EAST);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        mainFrame.setContentPane(mainPanel);
        mainFrame.setVisible(true);

        // Window listener (exit cleanly)
        mainFrame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                try {
                    if (dos != null) {
                        dos.writeUTF("MSG");
                        dos.writeUTF("exit");
                    }
                } catch (IOException ignored) {}
                closeConnection();
                System.exit(0);
            }
        });

        // Font autoscale when frame resized / maximized
        mainFrame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> {
                    int h = mainFrame.getHeight();
                    int newSize = baseChatFontSize + Math.max(0, (h - 550) / 80);
                    chatPane.setFont(new Font("Segoe UI", Font.PLAIN, newSize));
                });
            }
        });

        startConnection();

        ActionListener sendAction = e -> sendMessage();
        sendButton.addActionListener(sendAction);
        inputField.addActionListener(sendAction);

        fileButton.addActionListener(e -> sendFile());
    }

    // Settings dialog (theme toggle, notifications, change username)
    private void showSettingsDialog() {
        JDialog dlg = new JDialog(mainFrame, "Settings", true);
        dlg.setSize(360, 220);
        dlg.setLocationRelativeTo(mainFrame);
        dlg.setLayout(new BorderLayout(8,8));

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        // Theme toggle
        JPanel themePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        themePanel.setOpaque(false);
        JLabel themeLabel = new JLabel("Theme:");
        JToggleButton themeToggle = new JToggleButton(darkTheme ? "Dark" : "Light");
        themeToggle.setSelected(darkTheme);
        themeToggle.addActionListener(e -> {
            darkTheme = themeToggle.isSelected();
            themeToggle.setText(darkTheme ? "Dark" : "Light");
            applyTheme();
        });
        themePanel.add(themeLabel);
        themePanel.add(themeToggle);
        center.add(themePanel);

        // Notifications
        JPanel notifPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        notifPanel.setOpaque(false);
        JCheckBox notifBox = new JCheckBox("Enable Notifications", notificationsEnabled);
        notifBox.setOpaque(false);
        notifBox.addActionListener(e -> notificationsEnabled = notifBox.isSelected());
        notifPanel.add(notifBox);
        center.add(notifPanel);

        // Change username
        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        namePanel.setOpaque(false);
        JLabel nameLbl = new JLabel("Change Username:");
        JTextField nameField = new JTextField(userName, 10);
        JButton changeBtn = new JButton("Change");
        changeBtn.addActionListener(e -> {
            String newName = nameField.getText().trim();
            if (!newName.isEmpty() && !newName.equals(userName)) {
                try {
                    dos.writeUTF("NAMECHANGE");
                    dos.writeUTF(newName);
                    userName = newName;
                    mainFrame.setTitle("ChatX - " + userName);
                    // Update header label by recreating or setting text - easiest: append small notice
                    appendText("🔁 You changed your name to: " + userName + "\n");
                } catch (IOException ex) {
                    appendText("⚠ Failed to change name\n");
                }
            }
        });
        namePanel.add(nameLbl);
        namePanel.add(nameField);
        namePanel.add(changeBtn);
        center.add(namePanel);

        dlg.add(center, BorderLayout.CENTER);

        JButton close = new JButton("Close");
        close.addActionListener(e -> dlg.dispose());
        dlg.add(close, BorderLayout.SOUTH);

        dlg.setVisible(true);
    }

    private void applyTheme() {
        // Improved light/dark theme switch that keeps UI readable.
        // ONLY updates colors for components we control (chatPane, inputField, usersList, and their viewports)
        SwingUtilities.invokeLater(() -> {
            if (darkTheme) {
                // Dark theme (original)
                chatPane.setBackground(new Color(15, 35, 65));
                chatPane.setForeground(Color.WHITE);
                chatPane.setCaretColor(Color.WHITE);

                inputField.setBackground(new Color(30, 50, 80));
                inputField.setForeground(Color.WHITE);
                inputField.setCaretColor(Color.WHITE);

                usersList.setBackground(new Color(20, 40, 70));
                usersList.setForeground(Color.WHITE);

                // Update viewport backgrounds if present
                Container vp = chatPane.getParent();
                if (vp instanceof JViewport) vp.setBackground(new Color(15, 35, 65));

                // For usersList viewport
                Container uvp = usersList.getParent();
                if (uvp instanceof JViewport) uvp.setBackground(new Color(20, 40, 70));
            } else {
                // Light theme — choose gentle light colors that preserve readability and contrast
                chatPane.setBackground(new Color(245, 245, 245)); // very light gray (not pure white)
                chatPane.setForeground(new Color(30, 30, 30));   // dark text
                chatPane.setCaretColor(new Color(30, 30, 30));

                inputField.setBackground(Color.WHITE);
                inputField.setForeground(new Color(30, 30, 30));
                inputField.setCaretColor(new Color(30, 30, 30));

                usersList.setBackground(new Color(235, 240, 245)); // soft light panel
                usersList.setForeground(new Color(30, 30, 30));

                // Update viewport backgrounds if present
                Container vp = chatPane.getParent();
                if (vp instanceof JViewport) vp.setBackground(new Color(245, 245, 245));

                Container uvp = usersList.getParent();
                if (uvp instanceof JViewport) uvp.setBackground(new Color(235, 240, 245));
            }

            // If notifications disabled, do not show popups; theme itself does not affect that.
            // repaint main frame to ensure changes take effect
            if (mainFrame != null) {
                mainFrame.revalidate();
                mainFrame.repaint();
            }
        });
    }

    // About dialog
    private void showAboutDialog() {
        String text = "Project: ChatX\nDeveloped by Aditi Chand\nDescription: ChatX - Group Chat Application (TCP-based)  \n" +
                "\n" +
                "✅ Group Chat (TCP-based) - Multiple clients, broadcast messaging.  \n" +
                "✅ Modern Swing UI - Dark blue gradient, rounded edges, header + logo.  \n" +
                "✅ User Features - Username input, join/leave notifications.  \n" +
                "✅ Chat History - Messages logged to chatlog2.txt.  \n" +
                "✅ Exit Command - Safe disconnect + server port release.  \n" +
                "✅ File Sharing \uD83D\uDCC1 - Send/receive files (saved in downloads/).  \n" +
                "✅ Private Chat \uD83D\uDCAC - 1-to-1 messaging with delivery status ✔️✔️.  \n" +
                "✅ Authentication \uD83D\uDD11 - Login/Signup support.  \n" +
                "✅ Settings ⚙️ - Notifications, history, themes.  \n" +
                "✅ Dark/Light Theme \uD83C\uDF19☀️ - Switch modes anytime.";
        JOptionPane.showMessageDialog(mainFrame, text, "About ChatX", JOptionPane.INFORMATION_MESSAGE);
    }

    private void startConnection() {
        try {
            socket = new Socket("localhost", 1234);
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());

            // ensure downloads folder exists (Issue 2)
            File dl = new File(DOWNLOADS_BASE);
            if (!dl.exists()) dl.mkdirs();

            // send username first
            dos.writeUTF(userName);

            Thread receiveThread = new Thread(() -> {
                try {
                    while (true) {
                        String type = dis.readUTF();
                        if ("MSG".equals(type)) {
                            String msg = dis.readUTF();
                            appendText(msg + "\n");

                            // If message is "username: message", send READ receipt back to server
                            // parse simple format
                            if (msg.contains(": ")) {
                                String[] parts = msg.split(": ", 2);
                                if (parts.length == 2) {
                                    String originalSender = parts[0];
                                    String messageText = parts[1];
                                    // send READ for group messages (private ones handled separately)
                                    try {
                                        dos.writeUTF("READ");
                                        dos.writeUTF(originalSender);
                                        dos.writeUTF(messageText);
                                    } catch (IOException ignored) {}
                                }
                            }
                        } else if ("USERS".equals(type)) {
                            String usersCsv = dis.readUTF();
                            updateUsersList(usersCsv);
                        } else if ("FILE".equals(type)) {
                            String sender = dis.readUTF();
                            String fileName = dis.readUTF();
                            long fileSize = dis.readLong();

                            // save to fixed folder (Issue 2)
                            File dir = new File(DOWNLOADS_BASE);
                            if (!dir.exists()) dir.mkdirs();
                            File file = new File(dir, fileName);

                            try (FileOutputStream fos = new FileOutputStream(file)) {
                                byte[] buffer = new byte[4096];
                                long remaining = fileSize;
                                int read;
                                while (remaining > 0 &&
                                        (read = dis.read(buffer, 0, (int)Math.min(buffer.length, remaining))) != -1) {
                                    fos.write(buffer, 0, read);
                                    remaining -= read;
                                }
                            }

                            if (fileName.toLowerCase().endsWith(".jpg") || fileName.toLowerCase().endsWith(".png")) {
                                showImage(sender, file);
                            } else {
                                showFileWithSave(sender, file);
                            }

                            // send READ receipt for files
                            try {
                                dos.writeUTF("READ");
                                dos.writeUTF(sender);
                                dos.writeUTF(fileName);
                            } catch (IOException ignored) {}
                        } else if ("STATUS".equals(type)) {
                            String status = dis.readUTF();
                            appendText(status + "\n");
                        } else {
                            // ignore unknown
                        }
                    }
                } catch (IOException e) {
                    appendText("❌ Disconnected from server\n");
                }
            });
            receiveThread.start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "⚠ Error: " + e.getMessage(), "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Private chat sending logic: if a user is selected, send /pm <user> <msg>
    private void sendMessage() {
        try {
            String msg = inputField.getText();
            if (msg == null || msg.trim().isEmpty()) return;

            String selected = usersList.getSelectedValue();
            String payload;

            if (selected != null && !selected.trim().isEmpty() && !selected.equalsIgnoreCase(userName)) {
                payload = "/pm " + selected + " " + msg;
                appendText("✔ Sent (to " + selected + "): " + msg + "\n");
            } else {
                payload = msg; // group
                appendText("✔ Sent: " + msg + "\n");
            }

            String base64 = encryptToBase64AES(payload);
            dos.writeUTF("MSG_AES");
            dos.writeUTF(base64);

            inputField.setText("");
        } catch (Exception ex) {
            appendText("⚠ Failed to send message\n");
        }
    }

    private void sendFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(mainFrame) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] data = fis.readAllBytes();
                dos.writeUTF("FILE");
                dos.writeUTF(file.getName());
                dos.writeLong(data.length);
                dos.write(data);
                appendText("✔ Sent: " + file.getName() + "\n");
            } catch (IOException e) {
                appendText("⚠ Failed to send file\n");
            }
        }
    }

    private void showFileWithSave(String sender, File file) {
        SwingUtilities.invokeLater(() -> {
            appendText("📥 File received from " + sender + ": " + file.getName() + " ");
            JButton saveBtn = new JButton("💾 Save");
            saveBtn.addActionListener(ev -> {
                try {
                    File saveDir = new File(DOWNLOADS_BASE);
                    if (!saveDir.exists()) saveDir.mkdirs();
                    File saveFile = new File(saveDir, file.getName());
                    try (InputStream in = new FileInputStream(file);
                         OutputStream out = new FileOutputStream(saveFile)) {
                        in.transferTo(out);
                    }
                    if (notificationsEnabled) {
                        JOptionPane.showMessageDialog(mainFrame, "Saved to: " + saveFile.getAbsolutePath());
                    }
                } catch (IOException ex) {
                    if (notificationsEnabled) {
                        JOptionPane.showMessageDialog(mainFrame, "⚠ Failed to save file");
                    }
                }
            });
            chatPane.insertComponent(saveBtn);
            appendText("\n");
        });
    }

    private void showImage(String sender, File file) {
        SwingUtilities.invokeLater(() -> {
            try {
                ImageIcon icon = new ImageIcon(file.getAbsolutePath()); // absolute path to avoid missing image
                Image scaled = icon.getImage().getScaledInstance(200, -1, Image.SCALE_SMOOTH);
                ImageIcon scaledIcon = new ImageIcon(scaled);

                JLabel imgLabel = new JLabel(scaledIcon);
                imgLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
                imgLabel.setToolTipText("Click to view full image / Right-click to save");

                JPopupMenu menu = new JPopupMenu();
                JMenuItem saveItem = new JMenuItem("Save to Downloads");
                saveItem.addActionListener(ev -> {
                    try {
                        File saveDir = new File(DOWNLOADS_BASE);
                        if (!saveDir.exists()) saveDir.mkdirs();
                        File saveFile = new File(saveDir, file.getName());
                        try (InputStream in = new FileInputStream(file);
                             OutputStream out = new FileOutputStream(saveFile)) {
                            in.transferTo(out);
                        }
                        if (notificationsEnabled) {
                            JOptionPane.showMessageDialog(mainFrame, "Saved to: " + saveFile.getAbsolutePath());
                        }
                    } catch (IOException ex) {
                        if (notificationsEnabled) {
                            JOptionPane.showMessageDialog(mainFrame, "⚠ Failed to save image");
                        }
                    }
                });
                menu.add(saveItem);

                imgLabel.addMouseListener(new MouseAdapter() {
                    public void mouseClicked(MouseEvent e) {
                        if (SwingUtilities.isRightMouseButton(e)) {
                            menu.show(imgLabel, e.getX(), e.getY());
                        } else if (SwingUtilities.isLeftMouseButton(e)) {
                            ImageIcon fullIcon = new ImageIcon(file.getAbsolutePath());
                            JLabel fullLabel = new JLabel(fullIcon);
                            JScrollPane sp = new JScrollPane(fullLabel);
                            sp.setPreferredSize(new Dimension(600, 400));
                            JOptionPane.showMessageDialog(mainFrame, sp, "Image from " + sender, JOptionPane.PLAIN_MESSAGE);
                        }
                    }
                });

                appendText("📥 Image received from " + sender + ": " + file.getName() + "\n");
                chatPane.insertComponent(imgLabel);
                appendText("\n");
            } catch (Exception ex) {
                appendText("⚠ Failed to preview image\n");
            }
        });
    }

    // AES encrypt helper
    private String encryptToBase64AES(String plainText) throws Exception {
        byte[] iv = new byte[16];
        RANDOM.nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(AES_KEY, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
        byte[] cipherText = cipher.doFinal(plainText.getBytes("UTF-8"));

        byte[] combined = new byte[iv.length + cipherText.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    private void updateUsersList(String usersCsv) {
        SwingUtilities.invokeLater(() -> {
            try {
                usersModel.clear();
                if (usersCsv != null && !usersCsv.trim().isEmpty()) {
                    String[] parts = usersCsv.split(",");
                    for (String u : parts) usersModel.addElement(u);
                }
            } catch (Exception ignored) {}
        });
    }

    private void appendText(String text) {
        SwingUtilities.invokeLater(() -> {
            try {
                chatPane.getDocument().insertString(chatPane.getDocument().getLength(), text, null);
                chatPane.setCaretPosition(chatPane.getDocument().getLength());
            } catch (Exception ignored) {}
        });
    }

    private void closeConnection() {
        try {
            if (dis != null) dis.close();
            if (dos != null) dos.close();
            if (socket != null) socket.close();
        } catch (IOException ignored) {}
    }
}
