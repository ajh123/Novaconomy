package us.teaminceptus.novaconomy;

import io.netty.channel.Channel;
import net.md_5.bungee.api.chat.BaseComponent;
import net.minecraft.server.v1_8_R1.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_8_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_8_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_8_R1.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.material.Crops;
import org.bukkit.scheduler.BukkitRunnable;
import us.teaminceptus.novaconomy.abstraction.NBTWrapper;
import us.teaminceptus.novaconomy.abstraction.NovaInventory;
import us.teaminceptus.novaconomy.abstraction.Wrapper;
import us.teaminceptus.novaconomy.api.NovaConfig;
import us.teaminceptus.novaconomy.v1_8_R1.NBTWrapper1_8_R1;
import us.teaminceptus.novaconomy.v1_8_R1.NovaInventory1_8_R1;
import us.teaminceptus.novaconomy.v1_8_R1.PacketHandler1_8_R1;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.function.Consumer;

public final class Wrapper1_8_R1 implements Wrapper {

    @Override
    public int getCommandVersion() { return 2; }

    @Override
    public void sendActionbar(Player p, String message) {
        PacketPlayOutChat packet = new PacketPlayOutChat(new ChatComponentText(message), (byte)2);
        ((CraftPlayer) p).getHandle().playerConnection.sendPacket(packet);
    }

    @Override
    public void sendActionbar(Player p, BaseComponent component) {
        sendActionbar(p, component.toLegacyText());
    }

    @Override
    public ItemStack createSkull(OfflinePlayer p) {
        ItemStack item = new ItemStack(Material.SKULL_ITEM, 1, (short) 3);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwner(p.getName());
        item.setItemMeta(meta);

        return item;
    }

    @Override
    public ItemStack normalize(ItemStack item) {
        net.minecraft.server.v1_8_R1.ItemStack nmsitem = CraftItemStack.asNMSCopy(item);
        NBTTagCompound tag = nmsitem.hasTag() ? nmsitem.getTag() : new NBTTagCompound();

        tag.remove("id");
        tag.remove("Count");
        nmsitem.setTag(tag);
        return CraftItemStack.asBukkitCopy(nmsitem);
    }


    @Override
    public boolean isAgeable(Block b) {
        return b.getState().getData() instanceof Crops;
    }

    @Override
    public void removeItem(PlayerInteractEvent e) {
        e.getPlayer().setItemInHand(null);
    }

    @Override
    public boolean isCrop(Material m) {
        return Crops.class.isAssignableFrom(m.getData());
    }

    @Override
    public NovaInventory createInventory(String id, String name, int size) {
        return new NovaInventory1_8_R1(id, name, size);
    }

    @Override
    public NBTWrapper createNBTWrapper(org.bukkit.inventory.ItemStack item) {
        return new NBTWrapper1_8_R1(item);
    }

    @Override
    public void addPacketInjector(Player p) {
        EntityPlayer sp = ((CraftPlayer) p).getHandle();
        NetworkManager manager = sp.playerConnection.networkManager;

        try {
            Field f = NetworkManager.class.getDeclaredField("i");
            f.setAccessible(true);

            Channel ch = (Channel) f.get(manager);
            if (ch.pipeline().get(PACKET_INJECTOR_ID) != null) return;

            ch.pipeline().addAfter("decoder", PACKET_INJECTOR_ID, new PacketHandler1_8_R1(p));
        } catch (ReflectiveOperationException e) {
            NovaConfig.print(e);
        }
    }

    @Override
    public void removePacketInjector(Player p) {
        EntityPlayer sp = ((CraftPlayer) p).getHandle();
        NetworkManager manager = sp.playerConnection.networkManager;
        
        try {
            Field f = NetworkManager.class.getDeclaredField("i");
            f.setAccessible(true);

            Channel ch = (Channel) f.get(manager);

            if (ch.pipeline().get(PACKET_INJECTOR_ID) == null) return;
            ch.pipeline().remove(PACKET_INJECTOR_ID);
         } catch (ReflectiveOperationException e) {
            NovaConfig.print(e);
        }


    }

    @Override
    public void sendSign(Player p, Consumer<String[]> lines) {
        addPacketInjector(p);

        Location l = p.getLocation();
        WorldServer ws = ((CraftWorld) l.getWorld()).getHandle();
        BlockPosition pos = new BlockPosition(l.getBlockX(), 255, l.getBlockZ());

        PacketPlayOutBlockChange sent1 = new PacketPlayOutBlockChange(ws, pos);
        sent1.block = Blocks.STANDING_SIGN.getBlockData();

        ((CraftPlayer) p).getHandle().playerConnection.sendPacket(sent1);

        PacketPlayOutOpenSignEditor sent2 = new PacketPlayOutOpenSignEditor(pos);
        ((CraftPlayer) p).getHandle().playerConnection.sendPacket(sent2);

        PacketHandler1_8_R1.PACKET_HANDLERS.put(p.getUniqueId(), packetO -> {
            if (!(packetO instanceof PacketPlayInUpdateSign)) return false;
            PacketPlayInUpdateSign packet = (PacketPlayInUpdateSign) packetO;

            lines.accept(Arrays.stream(packet.b())
                    .map(IChatBaseComponent::getText)
                    .toArray(String[]::new));
            return true;
        });

        new BukkitRunnable() {
            @Override
            public void run() {
                PacketPlayOutBlockChange sent3 = new PacketPlayOutBlockChange(ws, pos);
                sent3.block = Blocks.AIR.getBlockData();

                ((CraftPlayer) p).getHandle().playerConnection.sendPacket(sent3);
            }
        }.runTaskLater(NovaConfig.getPlugin(), 2L);
    }

}