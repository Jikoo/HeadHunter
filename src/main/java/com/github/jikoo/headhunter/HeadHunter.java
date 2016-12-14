package com.github.jikoo.headhunter;

import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin granting mobs to have a configurable chance to drop heads when killed by a player.
 * 
 * @author Jikoo
 */
public class HeadHunter extends JavaPlugin implements Listener {

	private final HashMap<EntityType, Pair<Pair<Double, Double>, Pair<ItemStack, String>>> entityTypeHeads = new HashMap<>();

	@Override
	public void onEnable() {
		this.saveDefaultConfig();

		if (!this.getConfig().isConfigurationSection("entities")) {
			return;
		}

		ConfigurationSection entities = this.getConfig().getConfigurationSection("entities");

		for (String path : entities.getKeys(false)) {
			if (!entities.isConfigurationSection(path)) {
				this.getLogger().warning("Ignoring entity " + path + ": invalid configuration section");
				continue;
			}

			EntityType type;
			try {
				type = EntityType.valueOf(path);
			} catch (IllegalArgumentException e) {
				this.getLogger().warning("Ignoring entity " + path + ": invalid entity type");
				continue;
			}

			ConfigurationSection entity = entities.getConfigurationSection(path);

			double baseChance = entity.getDouble("chance.base", 0);
			double lootingModifier = Math.max(0, entity.getDouble("chance.lootingModifier", 0));

			if (baseChance <= 0 && lootingModifier <= 0) {
				this.getLogger().warning("Ignoring entity " + path + ": drop chances <= 0");
				continue;
			}

			ItemStack stack = entity.getItemStack("item");

			if (stack == null) {
				this.getLogger().warning("Ignoring entity " + path + ": null drop");
				continue;
			}

			entityTypeHeads.put(type, new ImmutablePair<>(new ImmutablePair<>(baseChance, lootingModifier),
					new ImmutablePair<>(stack, entity.getString("skullOwner", null))));
		}

		if (!entityTypeHeads.isEmpty()) {
			this.getServer().getPluginManager().registerEvents(this, this);
		}
	}

	@Override
	public void onDisable() {
		HandlerList.unregisterAll((Listener) this);
	}

	@EventHandler
	public void onEntityDeath(EntityDeathEvent event) {
		if (!entityTypeHeads.containsKey(event.getEntityType())) {
			return;
		}

		Player killer = event.getEntity().getKiller();

		if (killer == null || "false".equals(event.getEntity().getWorld().getGameRuleValue("doMobLoot"))) {
			return;
		}

		ItemStack hand = killer.getInventory().getItemInMainHand();
		int looting = hand.getType() == Material.AIR || !hand.containsEnchantment(Enchantment.LOOT_BONUS_MOBS)
				? 0 : hand.getEnchantmentLevel(Enchantment.LOOT_BONUS_MOBS);

		Pair<Pair<Double, Double>, Pair<ItemStack, String>> data = entityTypeHeads.get(event.getEntityType());

		double chance = data.getLeft().getLeft() + data.getLeft().getRight() * looting;

		if (chance <= 0 || ThreadLocalRandom.current().nextDouble() > chance) {
			return;
		}

		ItemStack head = data.getRight().getLeft().clone();
		String owner = data.getRight().getRight();
		if (head.getType() == Material.SKULL_ITEM && head.getDurability() == 3) {
			SkullMeta meta = (SkullMeta) head.getItemMeta();
			if (owner != null) {
				meta.setOwner(owner);
			} else if (event.getEntity() instanceof Player){
				meta.setOwner(event.getEntity().getName());
			}
		}

		event.getDrops().add(head);
	}

}
