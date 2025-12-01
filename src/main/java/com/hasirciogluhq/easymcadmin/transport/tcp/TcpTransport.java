package com.hasirciogluhq.easymcadmin.transport.tcp;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.gson.Gson;
import com.hasirciogluhq.easymcadmin.EasyMcAdmin;
import com.hasirciogluhq.easymcadmin.packets.generic.GenericPacket;
import com.hasirciogluhq.easymcadmin.packets.generic.Packet;
import com.hasirciogluhq.easymcadmin.transport.TransportInterface;
import com.hasirciogluhq.easymcadmin.transport.TransportListener;

public class TcpTransport implements TransportInterface {
    private Socket socket;
    private String host;
    private int port;
    private boolean isConnected = false;
    private boolean wasConnected = false;
    private EasyMcAdmin plugin;
    private Thread connectionThread;
    private TransportListener transportListener;
    private DataInputStream dataInputStream;
    private DataOutputStream dataOutputStream;
    private BlockingQueue<Packet> packetQueue;
    private Gson gson;

    public TcpTransport(EasyMcAdmin plugin, String host, int port) {
        this.plugin = plugin;
        this.host = host;
        this.port = port;
        this.packetQueue = new LinkedBlockingQueue<>();
        this.gson = new Gson();
    }

    public void connect() {
        try {
            socket = new Socket();
            socket.setSoTimeout(300000); // 5 minute timeout for read operations (long timeout)
            socket.setKeepAlive(true); // Enable TCP keep-alive
            socket.setTcpNoDelay(true); // Disable Nagle's algorithm for lower latency
            socket.connect(new InetSocketAddress(host, port), 10000); // 10 second connection timeout
            dataInputStream = new DataInputStream(socket.getInputStream());
            dataOutputStream = new DataOutputStream(socket.getOutputStream());
            isConnected = true;
            wasConnected = true;

            plugin.getLogger().info("TCP connected to " + host + ":" + port);

            if (transportListener != null) {
                transportListener.onConnect();
            }

            connectionThread = new Thread(() -> {
                while (isConnected && !socket.isClosed() && !Thread.currentThread().isInterrupted()) {
                    try {
                        // Read packet length (4 bytes - int)
                        int packetLength = dataInputStream.readInt();

                        if (packetLength <= 0 || packetLength > 10 * 1024 * 1024) { // Max 10MB
                            isConnected = false;
                            break;
                        }

                        // Read packet data (length bytes)
                        byte[] packetData = new byte[packetLength];
                        int totalRead = 0;
                        while (totalRead < packetLength) {
                            int bytesRead = dataInputStream.read(packetData, totalRead, packetLength - totalRead);
                            if (bytesRead == -1) {
                                isConnected = false;
                                break;
                            }
                            totalRead += bytesRead;
                        }

                        if (!isConnected) {
                            break;
                        }

                        // Convert byte array to JSON string
                        String jsonString = new String(packetData, StandardCharsets.UTF_8);

                        // Deserialize JSON to Packet
                        try {
                            Packet packet = new GenericPacket(jsonString);
                            packetQueue.offer(packet);

                            // Notify listener if available
                            if (transportListener != null) {
                                transportListener.onPacket(packet);
                            }
                        } catch (Exception e) {
                            if (transportListener != null) {
                                transportListener.onError(e);
                            }
                        }

                    } catch (SocketTimeoutException e) {
                        // Timeout - connection might still be alive, just no data
                        // TCP keep-alive will handle connection health check
                        // Continue loop instead of disconnecting
                        plugin.getLogger().fine("Read timeout (connection still alive)");
                        continue;
                    } catch (IOException e) {
                        // Check if it's a timeout error (some implementations throw IOException for timeout)
                        if (e.getMessage() != null && e.getMessage().contains("timeout")) {
                            plugin.getLogger().fine("Read timeout (connection still alive)");
                            continue;
                        }
                        
                        // Real connection error - disconnect
                        if (isConnected) {
                            if (transportListener != null) {
                                transportListener.onError(e);
                            }
                        }
                        isConnected = false;
                        break;
                    } catch (Exception e) {
                        if (transportListener != null) {
                            transportListener.onError(e);
                        }
                        isConnected = false;
                        break;
                    }
                }

                onDisconnected();
            });
            connectionThread.setName("EasyMcAdmin-TCP-Connection");
            connectionThread.setDaemon(true);
            connectionThread.start();
        } catch (IOException e) {
            isConnected = false;
            if (transportListener != null) {
                transportListener.onError(e);
            }
        }
    }

    public void disconnect() {
        try {
            isConnected = false;
            
            // Interrupt connection thread if it's waiting
            if (connectionThread != null && connectionThread.isAlive()) {
                connectionThread.interrupt();
            }
            
            // Close streams first
            if (dataInputStream != null) {
                try {
                    dataInputStream.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
            
            if (dataOutputStream != null) {
                try {
                    dataOutputStream.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
            
            // Close socket
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            
            if (wasConnected) {
                wasConnected = false;
            }
            
            if (transportListener != null) {
                transportListener.onDisconnect();
            }
        } catch (IOException e) {
            if (transportListener != null) {
                transportListener.onError(e);
            }
        }
    }

    public boolean isConnected() {
        return isConnected && socket != null && socket.isConnected() && !socket.isClosed();
    }

    public void sendPacket(Packet packet) {
        if (!isConnected() || dataOutputStream == null) {
            if (transportListener != null) {
                transportListener.onError(new IOException("Cannot send packet: not connected"));
            }
            return;
        }

        try {
            // Serialize packet to JSON
            String jsonString = gson.toJson(packet.toJson());
            byte[] jsonBytes = jsonString.getBytes(StandardCharsets.UTF_8);

            // Write packet length (4 bytes - int)
            dataOutputStream.writeInt(jsonBytes.length);

            // Write packet data
            dataOutputStream.write(jsonBytes);
            dataOutputStream.flush();

        } catch (IOException e) {
            isConnected = false;
            if (transportListener != null) {
                transportListener.onError(e);
            }
        } catch (Exception e) {
            if (transportListener != null) {
                transportListener.onError(e);
            }
        }
    }

    private void onDisconnected() {
        isConnected = false;
        if (wasConnected) {
            wasConnected = false;
        }
        if (transportListener != null) {
            transportListener.onDisconnect();
        }
    }

    public void setTransportListener(TransportListener transportListener) {
        this.transportListener = transportListener;
    }
}