package info.plugin.minecraft.fastXP;

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class FastXP extends JavaPlugin implements Listener, CommandExecutor {

    private NamespacedKey fastXpKey;

    @Override
    public void onEnable() {
        this.fastXpKey = new NamespacedKey(this, "fastxp_enabled");
        getServer().getPluginManager().registerEvents(this, this);

        if (getCommand("fastxp") != null) {
            getCommand("fastxp").setExecutor(this);
        }

        getLogger().info("FastXP erfolgreich aktiviert!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cDieser Befehl kann nur von Spielern ausgeführt werden.");
            return true;
        }

        boolean currentState = isFastXpEnabled(player);
        boolean newState = !currentState;
        setFastXpEnabled(player, newState);

        if (newState) {
            player.sendMessage("§aFastXP wurde §2aktiviert§a!");
        } else {
            player.sendMessage("§cFastXP wurde §4deaktiviert§c!");
        }

        return true;
    }

    private boolean isFastXpEnabled(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Byte value = pdc.get(fastXpKey, PersistentDataType.BYTE);
        // Standardmäßig für alle Spieler aktiv (true)
        return value == null || value == (byte) 1;
    }

    private void setFastXpEnabled(Player player, boolean enabled) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(fastXpKey, PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isFastXpEnabled(player)) return;

        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        collectNearbyXP(player, 2.5);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onXPPickup(PlayerPickupExperienceEvent event) {
        Player player = event.getPlayer();
        if (!isFastXpEnabled(player)) return;

        collectNearbyXP(player, 3.0);
        event.setCancelled(true);
    }

    private void collectNearbyXP(Player player, double radius) {
        int totalExp = 0;

        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof ExperienceOrb orb && orb.isValid()) {
                totalExp += orb.getExperience();
                orb.remove();
            }
        }

        if (totalExp > 0) {
            int remainingExp = applyMending(player, totalExp);

            if (remainingExp > 0) {
                player.giveExp(remainingExp);
            }

            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.0F);
        }
    }

    private int applyMending(Player player, int exp) {
        EntityEquipment equip = player.getEquipment();
        if (equip == null) return exp;

        List<ItemStack> repairableItems = new ArrayList<>();

        for (ItemStack item : equip.getArmorContents()) {
            if (isMendingRepairable(item)) repairableItems.add(item);
        }
        if (isMendingRepairable(equip.getItemInMainHand())) repairableItems.add(equip.getItemInMainHand());
        if (isMendingRepairable(equip.getItemInOffHand())) repairableItems.add(equip.getItemInOffHand());

        while (exp > 0 && !repairableItems.isEmpty()) {
            ItemStack item = repairableItems.get(ThreadLocalRandom.current().nextInt(repairableItems.size()));
            if (!(item.getItemMeta() instanceof Damageable meta) || !meta.hasDamage()) {
                repairableItems.remove(item);
                continue;
            }

            int currentDamage = meta.getDamage();
            int durabilityToRepair = Math.min(currentDamage, exp * 2);
            int expUsed = (int) Math.ceil(durabilityToRepair / 2.0);

            meta.setDamage(currentDamage - durabilityToRepair);
            item.setItemMeta(meta);

            exp -= expUsed;

            if (meta.getDamage() <= 0) {
                repairableItems.remove(item);
            }
        }

        return exp;
    }

    private boolean isMendingRepairable(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        if (!item.containsEnchantment(Enchantment.MENDING)) return false;
        return item.getItemMeta() instanceof Damageable meta && meta.hasDamage();
    }
}