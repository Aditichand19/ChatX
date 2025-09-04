# 💬 ChatX - Java-Based Multi-Client Chat Application

ChatX is a real-time multi-client chat application developed in Java using TCP socket programming. It supports **both one-to-one** and **group chat** functionalities and features a modern **Swing-based GUI**. Messages are transmitted reliably using TCP sockets, and all chat logs are saved on the server for reference.


# Features

* ✅ Group Chat (TCP-based) – Multiple clients connect to one server, messages are broadcasted.
* ✅ Private Chat – 1-to-1 messaging with delivery status (✔️✔).
* ✅ Modern Swing UI – Dark blue gradient, rounded edges, header + logo.
* ✅ User Features – Username input, join/leave notifications.
* ✅ Chat History Logging – All chats stored in chatlog3.txt (server-side).
* ✅ Exit Command – Safe disconnect + server announces leave message.
* ✅ File Sharing – Send/receive images, PDFs, docs (saved in downloads/ folder).
* ✅ Authentication – Login/Signup support.
* ✅ Settings – Change username, enable/disable notifications, theme toggle.
* ✅ Dark/Light Theme – Switch modes anytime.

# Tech Stack

* Language: Java
* GUI: Swing (custom gradient + modern look)
* Networking: TCP sockets (multi-client server architecture)
* File Handling: File logging + file transfer

## Project Structure

* ChatX/
* ├── src/
* │   ├── ChatServer.java      # Server-side (handles multiple clients, logging, broadcasting)
* │   ├── ChatClient.java      # Client-side with Swing UI
* │   ├── EncryptionUtils.java # For AES message encryption
* │   ├── ... (other helper files)
* │
* ├── downloads/              # Folder where received files are saved
* ├── chatlog3.txt            # Stores chat history (logs of all messages)
* ├── README.md               # Project documentation


# Future Enhancements

* Database integration (for authentication & history).
* Emoji support.
* Voice/video chat.

* 
