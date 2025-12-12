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
import java.util.concurrent.TimeUnit;

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
    // Outgoing queue and sender thread to avoid blocking caller threads on socket writes
    private BlockingQueue<Packet> outgoingQueue;
    private Thread senderThread;
    private int outgoingQueueCapacity;
    private long outgoingQueueOfferTimeoutMs;
    // Telemetry
    private static final java.util.concurrent.atomic.AtomicInteger enqueueFailures = new java.util.concurrent.atomic.AtomicInteger(0);
    private static volatile TcpTransport INSTANCE = null;
    private Gson gson;

    public TcpTransport(EasyMcAdmin plugin, String host, int port) {
        this.plugin = plugin;
        this.host = host;
        this.port = port;
        this.packetQueue = new LinkedBlockingQueue<>();
        // Read outgoing queue configuration from plugin config (fallback to defaults)
        try {
            this.outgoingQueueCapacity = Math.max(1, plugin.getConfig().getInt("transport.outgoing_queue_capacity", 5000));
        } catch (Exception e) {
            this.outgoingQueueCapacity = 5000;
        }

        try {
            this.outgoingQueueOfferTimeoutMs = Math.max(0, plugin.getConfig().getInt("transport.outgoing_queue_offer_timeout_ms", 200));
        } catch (Exception e) {
            this.outgoingQueueOfferTimeoutMs = 200;
        }

        this.outgoingQueue = new LinkedBlockingQueue<>(this.outgoingQueueCapacity);
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

            // register current instance for telemetry
            INSTANCE = this;

            // Start sender thread which consumes outgoingQueue and writes to socket
            senderThread = new Thread(() -> {
                while (isConnected && !Thread.currentThread().isInterrupted()) {
                    try {
                        Packet pkt = outgoingQueue.take(); // block until a packet is available
                        if (pkt == null) continue;

                        try {
                            // Serialize packet to JSON
                            String jsonString = gson.toJson(pkt.toJson());
                            byte[] jsonBytes = jsonString.getBytes(StandardCharsets.UTF_8);

                            synchronized (this) {
                                if (dataOutputStream == null) {
                                    throw new IOException("Output stream is null");
                                }
                                dataOutputStream.writeInt(jsonBytes.length);
                                dataOutputStream.write(jsonBytes);
                                dataOutputStream.flush();
                            }
                        } catch (IOException e) {
                            // On write error, notify listener and break to trigger disconnect
                            if (transportListener != null) {
                                transportListener.onError(e);
                            }
                            isConnected = false;
                            break;
                        }

                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        if (transportListener != null) {
                            transportListener.onError(e);
                        }
                        isConnected = false;
                        break;
                    }
                }

                // Sender thread exiting
            });
            senderThread.setName("EasyMcAdmin-TCP-Sender");
            senderThread.setDaemon(true);
            senderThread.start();
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

            // Stop sender thread
            if (senderThread != null && senderThread.isAlive()) {
                senderThread.interrupt();
            }

            // Clear outgoing queue to release memory
            if (outgoingQueue != null) {
                outgoingQueue.clear();
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
        // unregister telemetry instance
        INSTANCE = null;
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
        // Enqueue the packet to outgoing queue for the dedicated sender thread to write.
        try {
            boolean offered = outgoingQueue.offer(packet, this.outgoingQueueOfferTimeoutMs, TimeUnit.MILLISECONDS);
            if (!offered) {
                // Queue full or unable to enqueue in timely manner
                enqueueFailures.incrementAndGet();
                if (transportListener != null) {
                    transportListener.onError(new IOException("Outgoing queue full, cannot enqueue packet"));
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            if (transportListener != null) {
                transportListener.onError(new IOException("Interrupted while enqueueing packet"));
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
        // Stop sender thread if running
        if (senderThread != null && senderThread.isAlive()) {
            senderThread.interrupt();
        }
        if (transportListener != null) {
            transportListener.onDisconnect();
        }
        // unregister telemetry instance
        INSTANCE = null;
    }

    public static int getOutgoingQueueDepth() {
        TcpTransport t = INSTANCE;
        if (t == null || t.outgoingQueue == null) return 0;
        return t.outgoingQueue.size();
    }

    public static int getEnqueueFailures() {
        return enqueueFailures.get();
    }

    public void setTransportListener(TransportListener transportListener) {
        this.transportListener = transportListener;
    }
}