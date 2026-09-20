package com.verstile.client.module.utility;

import com.verstile.client.module.Category;
import com.verstile.client.module.Module;
import net.minecraft.network.protocol.Packet;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class Blink extends Module {
    private boolean chokingPackets = false;
    private final Queue<Packet<?>> queuedPackets = new ConcurrentLinkedQueue<>();

    public Blink() {
        super("Blink", "Chokes outbound network packets to simulate fake lag", Category.UTILITY, 0);
    }

    @Override
    public void onEnable() {
        chokingPackets = true;
    }

    @Override
    public void onDisable() {
        chokingPackets = false;
        if (client.getConnection() != null) {
            Packet<?> packet;
            while ((packet = queuedPackets.poll()) != null) client.getConnection().getConnection().send(packet);
        } else queuedPackets.clear();
    }

    public boolean isChokingPackets() {
        return chokingPackets;
    }

    public void queue(Packet<?> packet) { queuedPackets.add(packet); }

    public int queuedCount() { return queuedPackets.size(); }
}
