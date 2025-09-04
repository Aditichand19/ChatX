// filename:GroupChatServer.java
import java.io.*;
import java.net.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class GroupChatServer {
    private static final int PORT = 1234;

    // keep your specified path for chat log (Issue 1)
    private static final String LOG_FILE = "DRDO_Internship/ChatServer_more/src/chatlog3.txt";

    // AES key must match client (16 bytes)
    private static final byte[] AES_KEY = "0123456789ABCDEF".getBytes();

    private static final Set<ClientHandler> clients = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("📡 Server started on port " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("✅ New client: " + clientSocket);
                ClientHandler handler = new ClientHandler(clientSocket);
                clients.add(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.out.println("⚠ Server error: " + e.getMessage());
        }
    }

    static class ClientHandler implements Runnable {
        private final Socket socket;
        private DataInputStream dis;
        private DataOutputStream dos;
        private String clientName = "Unknown";

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                dis = new DataInputStream(socket.getInputStream());
                dos = new DataOutputStream(socket.getOutputStream());

                // Keep compatibility: prompt name
                dos.writeUTF("Enter your name:");
                clientName = dis.readUTF();
                if (clientName == null || clientName.trim().isEmpty()) clientName = "Unknown";

                broadcast("🔔 " + clientName + " joined the chat.", this);
                sendUsersUpdate();

                while (true) {
                    String type;
                    try {
                        type = dis.readUTF();
                    } catch (EOFException eof) {
                        break;
                    }

                    if ("MSG_AES".equals(type)) {
                        String base64Encrypted = dis.readUTF();
                        String message;
                        try {
                            message = decryptBase64AES(base64Encrypted);
                        } catch (Exception ex) {
                            message = "[INVALID AES PAYLOAD]";
                        }

                        if (message.equalsIgnoreCase("exit")) break;

                        if (message.startsWith("/pm ")) {
                            handlePrivateMessageRaw(message);
                        } else {
                            String formatted = clientName + ": " + message;
                            broadcast(formatted, this);

                            // Notify sender that message was delivered once
                            try {
                                dos.writeUTF("STATUS");
                                dos.writeUTF("☑ Delivered: " + message);
                            } catch (IOException ignored) {}
                        }
                    } else if ("MSG".equals(type)) { // plaintext fallback
                        String message = dis.readUTF();
                        if (message.equalsIgnoreCase("exit")) break;

                        if (message.startsWith("/pm ")) {
                            handlePrivateMessageRaw(message);
                        } else {
                            String formatted = clientName + ": " + message;
                            broadcast(formatted, this);

                            try {
                                dos.writeUTF("STATUS");
                                dos.writeUTF("☑ Delivered: " + message);
                            } catch (IOException ignored) {}
                        }
                    } else if ("FILE".equals(type)) {
                        String fileName = dis.readUTF();
                        long fileSize = dis.readLong();
                        byte[] buffer = new byte[(int) fileSize];
                        int read = 0;
                        int totalRead = 0;
                        while (totalRead < fileSize) {
                            read = dis.read(buffer, totalRead, (int)(fileSize - totalRead));
                            if (read == -1) break;
                            totalRead += read;
                        }
                        broadcastFile(clientName, fileName, buffer, this);

                        // notify sender delivered
                        try {
                            dos.writeUTF("STATUS");
                            dos.writeUTF("☑ Delivered: " + fileName);
                        } catch (IOException ignored) {}
                    } else if ("READ".equals(type)) {
                        // READ receipts: senderName + messageText
                        String originalSender = dis.readUTF();
                        String msgText = dis.readUTF();
                        // forward read receipt to original sender
                        ClientHandler original = findClientByName(originalSender);
                        if (original != null) {
                            try {
                                original.dos.writeUTF("STATUS");
                                original.dos.writeUTF("✅ Read: " + msgText + " (by " + clientName + ")");
                            } catch (IOException ignored) {}
                        }
                    } else if ("NAMECHANGE".equals(type)) {
                        String newName = dis.readUTF();
                        String old = clientName;
                        if (newName != null && !newName.trim().isEmpty()) {
                            clientName = newName;
                            broadcast("🔁 " + old + " changed name to " + clientName, this);
                            sendUsersUpdate();
                        }
                    } else {
                        System.out.println("⚠ Unknown type from " + clientName + ": " + type);
                    }
                }
            } catch (IOException e) {
                System.out.println("❌ Client error: " + e.getMessage());
            } finally {
                try {
                    clients.remove(this);
                    broadcast("🚪 " + clientName + " left the chat.", this);
                    sendUsersUpdate();
                    if (dis != null) dis.close();
                    if (dos != null) dos.close();
                    if (!socket.isClosed()) socket.close();
                    System.out.println("❎ Client disconnected: " + clientName);
                } catch (IOException ignored) {}
            }
        }

        // Private message routing
        private void handlePrivateMessageRaw(String raw) {
            try {
                String[] parts = raw.split(" ", 3);
                if (parts.length < 3) {
                    dos.writeUTF("MSG");
                    dos.writeUTF("⚠ Usage: /pm <username> <message>");
                    return;
                }
                String targetName = parts[1];
                String privateMsg = parts[2];

                ClientHandler target = findClientByName(targetName);

                if (target != null) {
                    String recvText = "🔒 Private from " + clientName + ": " + privateMsg;
                    String sendText = "🔒 Private to " + targetName + ": " + privateMsg;

                    // deliver
                    target.dos.writeUTF("MSG");
                    target.dos.writeUTF(recvText);

                    this.dos.writeUTF("MSG");
                    this.dos.writeUTF(sendText);

                    // log readable
                    logMessage("[PM] " + clientName + " -> " + targetName + ": " + privateMsg);
                } else {
                    this.dos.writeUTF("MSG");
                    this.dos.writeUTF("⚠ User " + targetName + " not found.");
                }
            } catch (IOException ignored) {}
        }

        private ClientHandler findClientByName(String name) {
            synchronized (clients) {
                for (ClientHandler c : clients) {
                    if (c.clientName.equalsIgnoreCase(name)) return c;
                }
            }
            return null;
        }

        // Broadcast plaintext once
        private void broadcast(String message, ClientHandler sender) {
            logMessage(message);
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    try {
                        client.dos.writeUTF("MSG");
                        client.dos.writeUTF(message);
                    } catch (IOException ignored) {}
                }
            }
        }

        private void broadcastFile(String sender, String fileName, byte[] fileData, ClientHandler from) {
            String info = "📁 " + sender + " sent a file: " + fileName;
            logMessage(info);
            synchronized (clients) {
                for (ClientHandler client : clients) {
                    try {
                        client.dos.writeUTF("FILE");
                        client.dos.writeUTF(sender);
                        client.dos.writeUTF(fileName);
                        client.dos.writeLong(fileData.length);
                        client.dos.write(fileData);
                    } catch (IOException ignored) {}
                }
            }
        }

        // Send "USERS" list to all
        private void sendUsersUpdate() {
            StringBuilder sb = new StringBuilder();
            synchronized (clients) {
                boolean first = true;
                for (ClientHandler c : clients) {
                    if (!first) sb.append(",");
                    sb.append(c.clientName == null ? "Unknown" : c.clientName);
                    first = false;
                }
                for (ClientHandler client : clients) {
                    try {
                        client.dos.writeUTF("USERS");
                        client.dos.writeUTF(sb.toString());
                    } catch (IOException ignored) {}
                }
            }
        }

        private void logMessage(String message) {
            System.out.println("[LOG] " + message);
            try {
                File f = new File(LOG_FILE);
                File parent = f.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                try (FileWriter fw = new FileWriter(f, true);
                     BufferedWriter bw = new BufferedWriter(fw);
                     PrintWriter writer = new PrintWriter(bw)) {
                    writer.println(message);
                }
            } catch (IOException ignored) {}
        }

        // AES decrypt helper
        private String decryptBase64AES(String base64WithIv) throws Exception {
            byte[] all = Base64.getDecoder().decode(base64WithIv);
            if (all.length < 16) throw new IllegalArgumentException("Invalid data");
            byte[] iv = Arrays.copyOfRange(all, 0, 16);
            byte[] ct = Arrays.copyOfRange(all, 16, all.length);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(AES_KEY, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] plain = cipher.doFinal(ct);
            return new String(plain, "UTF-8");
        }
    }
}
