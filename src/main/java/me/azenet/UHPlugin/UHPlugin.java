package me.azenet.UHPlugin;

import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.conversations.ConversationAbandonedListener;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.logging.Logger;

public final class UHPlugin extends JavaPlugin implements ConversationAbandonedListener {

	private Logger logger = null;
	private LinkedList<Location> loc = new LinkedList<Location>();
	private Random random = null;
	private ShapelessRecipe goldenMelon = null;
	private ShapedRecipe compass = null;
	private Integer episode = 0;
	private Boolean gameRunning = false;
	private Scoreboard sb = null;
	private Integer minutesLeft = 0;
	private Integer secondsLeft = 0;
	private NumberFormat formatter = new DecimalFormat("00");
	private String sbobjname = "KTP";
	private Boolean damageIsOn = false;
	private ArrayList<UHTeam> teams = new ArrayList<UHTeam>();
	private HashMap<String, ConversationFactory> cfs = new HashMap<String, ConversationFactory>();
	private UHPrompts uhp = null;
	private HashSet<String> deadPlayers = new HashSet<String>();
	
	@Override
	public void onEnable() {
		this.saveDefaultConfig();
		 
		File positions = new File("plugins/UHPlugin/positions.txt");
		if (positions.exists()) {
			BufferedReader br = null;
			try {
				br = new BufferedReader(new FileReader(positions));
				String line;
				while ((line = br.readLine()) != null) {
					String[] l = line.split(",");
					getLogger().info("Adding position "+Integer.parseInt(l[0])+","+Integer.parseInt(l[1])+" from positions.txt");
					addLocation(Integer.parseInt(l[0]), Integer.parseInt(l[1]));
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				try { if (br != null) br.close(); }
				catch (Exception e) { e.printStackTrace(); } //c tré l'inline
			}
			
		}
		uhp = new UHPrompts(this);
		logger = Bukkit.getLogger();
		logger.info("UHPlugin loaded");
		random = new Random();
		
		goldenMelon = new ShapelessRecipe(new ItemStack(Material.SPECKLED_MELON));
		goldenMelon.addIngredient(1, Material.GOLD_BLOCK);
		goldenMelon.addIngredient(1, Material.MELON);
		this.getServer().addRecipe(goldenMelon);
		
		if (getConfig().getBoolean("compass")) {
			compass = new ShapedRecipe(new ItemStack(Material.COMPASS));
			compass.shape(new String[] {"CIE", "IRI", "BIF"});
			compass.setIngredient('I', Material.IRON_INGOT);
			compass.setIngredient('R', Material.REDSTONE);
			compass.setIngredient('C', Material.SULPHUR);
			compass.setIngredient('E', Material.SPIDER_EYE);
			compass.setIngredient('B', Material.BONE);
			compass.setIngredient('F', Material.ROTTEN_FLESH);
			this.getServer().addRecipe(compass);
		}
		
		getServer().getPluginManager().registerEvents(new UHPluginListener(this), this);
		
		sb = Bukkit.getServer().getScoreboardManager().getNewScoreboard();
		Objective obj = sb.registerNewObjective("Vie", "dummy");
		obj.setDisplayName("Vie");
		obj.setDisplaySlot(DisplaySlot.PLAYER_LIST);
		
		setMatchInfo();
		
		getServer().getWorlds().get(0).setGameRuleValue("doDaylightCycle", "false");
		getServer().getWorlds().get(0).setTime(6000L);
		getServer().getWorlds().get(0).setStorm(false);
		getServer().getWorlds().get(0).setDifficulty(Difficulty.HARD);
		
		cfs.put("teamPrompt", new ConversationFactory(this)
		.withModality(true)
		.withFirstPrompt(uhp.getTNP())
		.withEscapeSequence("/cancel")
		.thatExcludesNonPlayersWithMessage("Il faut être un joueur ingame.")
		.withLocalEcho(false)
		.addConversationAbandonedListener(this));
		
		cfs.put("playerPrompt", new ConversationFactory(this)
		.withModality(true)
		.withFirstPrompt(uhp.getPP())
		.withEscapeSequence("/cancel")
		.thatExcludesNonPlayersWithMessage("Il faut être un joueur ingame.")
		.withLocalEcho(false)
		.addConversationAbandonedListener(this));
	}
	
	
	public void addLocation(int x, int z) {
		loc.add(new Location(getServer().getWorlds().get(0), x, getServer().getWorlds().get(0).getHighestBlockYAt(x,z)+120, z));
	}
	
	public void setMatchInfo() {
		Objective obj = null;
		try {
			obj = sb.getObjective(sbobjname);
			obj.setDisplaySlot(null);
			obj.unregister();
		} catch (Exception e) {
			
		}
		Random r = new Random();
		sbobjname = "KTP"+r.nextInt(10000000);
		obj = sb.registerNewObjective(sbobjname, "dummy");
		obj = sb.getObjective(sbobjname);

		obj.setDisplayName(this.getScoreboardName());
		obj.setDisplaySlot(DisplaySlot.SIDEBAR);
		obj.getScore(Bukkit.getOfflinePlayer(ChatColor.GRAY+"Episode "+ChatColor.WHITE+episode)).setScore(5);
		obj.getScore(Bukkit.getOfflinePlayer(ChatColor.WHITE+""+Bukkit.getServer().getOnlinePlayers().size()+ChatColor.GRAY+" joueurs")).setScore(4);
		obj.getScore(Bukkit.getOfflinePlayer(ChatColor.WHITE+""+getAliveTeams().size()+ChatColor.GRAY+" teams")).setScore(3);
		obj.getScore(Bukkit.getOfflinePlayer("")).setScore(2);
		obj.getScore(Bukkit.getOfflinePlayer(ChatColor.WHITE+formatter.format(this.minutesLeft)+ChatColor.GRAY+":"+ChatColor.WHITE+formatter.format(this.secondsLeft))).setScore(1);
	}

	private ArrayList<UHTeam> getAliveTeams() {
		ArrayList<UHTeam> aliveTeams = new ArrayList<UHTeam>();
		for (UHTeam t : teams) {
			for (Player p : t.getPlayers()) {
				if (p.isOnline() && !aliveTeams.contains(t)) aliveTeams.add(t);
			}
		}
		return aliveTeams;
	}

	@Override
	public void onDisable() {
		logger.info("UHPlugin unloaded");
	}
	
	public boolean onCommand(final CommandSender s, Command c, String l, String[] a) {
		if (c.getName().equalsIgnoreCase("uh")) {
			if (!(s instanceof Player)) {
				s.sendMessage(ChatColor.RED+"Vous devez être un joueur");
				return true;
			}
			Player pl = (Player)s;
			if (!pl.isOp()) {
				pl.sendMessage(ChatColor.RED+"Lolnope.");
				return true;
			}
			if (a.length == 0) {
				pl.sendMessage("Usage : /uh <start|shift|team|addspawn|generatewalls>");
				return true;
			}
			if (a[0].equalsIgnoreCase("start")) {
				if (teams.size() == 0) {
					for (Player p : getServer().getOnlinePlayers()) {
						UHTeam uht = new UHTeam(p.getName(), p.getName(), ChatColor.WHITE, this);
						uht.addPlayer(p);
						teams.add(uht);
					}
				}
				if (loc.size() < teams.size()) {
					s.sendMessage(ChatColor.RED+"Pas assez de positions de TP");
					return true;
				}
				LinkedList<Location> unusedTP = loc;
				for (final UHTeam t : teams) {
					final Location lo = unusedTP.get(this.random.nextInt(unusedTP.size()));
					Bukkit.getScheduler().runTaskLater(this, new BukkitRunnable() {

						@Override
						public void run() {
							t.teleportTo(lo);
							for (Player p : t.getPlayers()) {
								p.setGameMode(GameMode.SURVIVAL);
								p.setHealth(20);
								p.setFoodLevel(20);
								p.setExhaustion(5F);
								p.getInventory().clear();
								p.getInventory().setArmorContents(new ItemStack[] {new ItemStack(Material.AIR), new ItemStack(Material.AIR), 
										new ItemStack(Material.AIR), new ItemStack(Material.AIR)});
								p.setExp(0L+0F);
								p.setLevel(0);
								p.closeInventory();
								p.getActivePotionEffects().clear();
								p.setCompassTarget(lo);
								setLife(p, 20);
							}
						}
					}, 10L);
					
					unusedTP.remove(lo);
				}
				Bukkit.getScheduler().runTaskLater(this, new BukkitRunnable() {

					@Override
					public void run() {
						damageIsOn = true;
					}
				}, 600L);
				World w = Bukkit.getWorlds().get(0);
				w.setGameRuleValue("doDaylightCycle", ((Boolean)getConfig().getBoolean("daylightCycle.do")).toString());
				w.setTime(getConfig().getLong("daylightCycle.time"));
				w.setStorm(false);
				w.setDifficulty(Difficulty.HARD);
				this.episode = 1;
				this.minutesLeft = getEpisodeLength();
				this.secondsLeft = 0;
				Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new BukkitRunnable() {
					@Override
					public void run() {
						setMatchInfo();
						secondsLeft--;
						if (secondsLeft == -1) {
							minutesLeft--;
							secondsLeft = 59;
						}
						if (minutesLeft == -1) {
							minutesLeft = getEpisodeLength();
							secondsLeft = 0;
							Bukkit.getServer().broadcastMessage(ChatColor.AQUA+"-------- Fin episode "+episode+" --------");
							shiftEpisode();
						}
					} 
				}, 20L, 20L);
				
				Bukkit.getServer().broadcastMessage(ChatColor.GREEN+"--- GO ---");
				this.gameRunning = true;
				return true;
			} else if (a[0].equalsIgnoreCase("shift")) {
				Bukkit.getServer().broadcastMessage(ChatColor.AQUA+"-------- Fin episode "+episode+" [forcé par "+s.getName()+"] --------");
				shiftEpisode();
				this.minutesLeft = getEpisodeLength();
				this.secondsLeft = 0;
				return true;
			} else if (a[0].equalsIgnoreCase("team")) {
				Inventory iv = this.getServer().createInventory(pl, 54, "- Teams -");
				Integer slot = 0;
				ItemStack is = null;
				for (UHTeam t : teams) {
					is = new ItemStack(Material.BEACON, t.getPlayers().size());
					ItemMeta im = is.getItemMeta();
					im.setDisplayName(t.getChatColor()+t.getDisplayName());
					ArrayList<String> lore = new ArrayList<String>();
					for (Player p : t.getPlayers()) {
						lore.add("- "+p.getDisplayName());
					}
					im.setLore(lore);
					is.setItemMeta(im);
					iv.setItem(slot, is);
					slot++;
				}
				
				ItemStack is2 = new ItemStack(Material.DIAMOND);
				ItemMeta im2 = is2.getItemMeta();
				im2.setDisplayName(ChatColor.AQUA+""+ChatColor.ITALIC+"Créer une team");
				is2.setItemMeta(im2);
				iv.setItem(53, is2);
				
				pl.openInventory(iv);
				return true;
//			} else if (a[0].equalsIgnoreCase("newteam")) {
//				if (a.length != 4) {
//					pl.sendMessage(ChatColor.RED+"Usage: /uh newteam nom couleur nom nomAffiché");
//					return true;
//				}
//				if (a[1].length() > 16) {
//					pl.sendMessage(ChatColor.RED+"Le nom de la team ne doit pas faire plus de 16 chars");
//					return true;
//				}
//				if (a[3].length() > 32) {
//					pl.sendMessage(ChatColor.RED+"Le nom affiché de la team ne doit pas faire plus de 32 chars");
//				}
//				ChatColor cc;
//				try {
//					cc = ChatColor.valueOf(a[2].toUpperCase());
//				} catch (IllegalArgumentException e) {
//					pl.sendMessage(ChatColor.RED+"La couleur est invalide.");
//					return true;
//				}
//				teams.add(new UHTeam(a[1], a[3], cc, this));
//				pl.sendMessage(ChatColor.GREEN+"Team créée. Utilisez /uh playertoteam "+a[1]+" nomjoueur pour y ajouter des joueurs.");
//				return true;
//			} else if (a[0].equalsIgnoreCase("playertoteam")) {
//				if (a.length != 3) {
//					pl.sendMessage(ChatColor.RED+"Usage: /uh playertoteam nomteam nomjoueur");
//					return true;
//				}
//				UHTeam t = getTeam(a[1]);
//				if (t == null) {
//					pl.sendMessage(ChatColor.RED+"Team inexistante. /uh teams pour voir les teams");
//					return true;
//				}
//				if (Bukkit.getPlayerExact(a[2]) == null) {
//					pl.sendMessage(ChatColor.RED+"Le joueur est introuvable. (Il doit être connecté.)");
//					return true;
//				}
//				t.addPlayer(Bukkit.getPlayerExact(a[2]));
//				pl.sendMessage(ChatColor.GREEN+Bukkit.getPlayerExact(a[2]).getName()+" ajouté à la team "+a[1]+".");
//				return true;
//			} else if (a[0].equalsIgnoreCase("teams")) {
//				for (UHTeam t : teams) {
//					pl.sendMessage(ChatColor.DARK_GRAY+"- "+ChatColor.AQUA+t.getName()+ChatColor.DARK_GRAY+" ["+ChatColor.GRAY+t.getDisplayName()+ChatColor.DARK_GRAY+"] - "+ChatColor.GRAY+t.getPlayers().size()+ChatColor.DARK_GRAY+" joueurs");
//				}
//				return true;
			} else if (a[0].equalsIgnoreCase("addspawn")) {
				addLocation(pl.getLocation().getBlockX(), pl.getLocation().getBlockZ());
				pl.sendMessage(ChatColor.DARK_GRAY+"Position ajoutée: "+ChatColor.GRAY+pl.getLocation().getBlockX()+","+pl.getLocation().getBlockZ());
				return true;
			} else if (a[0].equalsIgnoreCase("generateWalls")) {
				pl.sendMessage(ChatColor.GRAY+"Génération en cours...");
				try {
					Integer halfMapSize = (int) Math.floor(this.getConfig().getInt("map.size")/2);
					Integer wallHeight = this.getConfig().getInt("map.wall.height");
					Material wallBlock = Material.getMaterial(this.getConfig().getInt("map.wall.block"));
					World w = pl.getWorld();
					
					Location spawn = w.getSpawnLocation();
					Integer limitXInf = spawn.add(-halfMapSize, 0, 0).getBlockX();
					
					spawn = w.getSpawnLocation();
					Integer limitXSup = spawn.add(halfMapSize, 0, 0).getBlockX();
					
					spawn = w.getSpawnLocation();
					Integer limitZInf = spawn.add(0, 0, -halfMapSize).getBlockZ();
					
					spawn = w.getSpawnLocation();
					Integer limitZSup = spawn.add(0, 0, halfMapSize).getBlockZ();
					
					for (Integer x = limitXInf; x <= limitXSup; x++) {
						w.getBlockAt(x, 1, limitZInf).setType(Material.BEDROCK);
						w.getBlockAt(x, 1, limitZSup).setType(Material.BEDROCK);
						for (Integer y = 2; y <= wallHeight; y++) {
							w.getBlockAt(x, y, limitZInf).setType(wallBlock);
							w.getBlockAt(x, y, limitZSup).setType(wallBlock);
						}
					} 
					
					for (Integer z = limitZInf; z <= limitZSup; z++) {
						w.getBlockAt(limitXInf, 1, z).setType(Material.BEDROCK);
						w.getBlockAt(limitXSup, 1, z).setType(Material.BEDROCK);
						for (Integer y = 2; y <= wallHeight; y++) {
							w.getBlockAt(limitXInf, y, z).setType(wallBlock);
							w.getBlockAt(limitXSup, y, z).setType(wallBlock);
						}
					} 
				} catch (Exception e) {
					e.printStackTrace();
					pl.sendMessage(ChatColor.RED+"Echec génération. Voir console pour détails.");
					return true;
				}
				pl.sendMessage(ChatColor.GRAY+"Génération terminée.");
				return true;
			}
		}
		return false;
	}
	
	public void shiftEpisode() {
		this.episode++;
	}
	
	public boolean isGameRunning() {
		return this.gameRunning;
	}

	public void updatePlayerListName(Player p) {
		p.setScoreboard(sb);
		Integer he = (int) Math.round(p.getHealth());
		sb.getObjective("Vie").getScore(p).setScore(he);
	}

	public void addToScoreboard(Player player) {
		player.setScoreboard(sb);
		sb.getObjective("Vie").getScore(player).setScore(0);
		this.updatePlayerListName(player);
	}

	public void setLife(Player entity, int i) {
		entity.setScoreboard(sb);
		sb.getObjective("Vie").getScore(entity).setScore(i);
	}

	public boolean isTakingDamage() {
		return damageIsOn;
	}
	
	public Scoreboard getScoreboard() {
		return sb;
	}
	
	public UHTeam getTeam(String name) {
		for(UHTeam t : teams) {
			if (t.getName().equalsIgnoreCase(name)) return t;
		}
		return null;
	}

	public UHTeam getTeamForPlayer(Player p) {
		for(UHTeam t : teams) {
			if (t.getPlayers().contains(p)) return t;
		}
		return null;
	}
	
	public Integer getEpisodeLength() {
		return this.getConfig().getInt("episodeLength");
	}

	@Override
	public void conversationAbandoned(ConversationAbandonedEvent abandonedEvent) {
		if (!abandonedEvent.gracefulExit()) {
			abandonedEvent.getContext().getForWhom().sendRawMessage(ChatColor.RED+"Abandonné par "+abandonedEvent.getCanceller().getClass().getName());
		}		
	}
	
	public boolean createTeam(String name, ChatColor color) {
		if (teams.size() <= 50) {
			teams.add(new UHTeam(name, name, color, this));
			return true;
		}
		return false;
	}
	public ConversationFactory getConversationFactory(String string) {
		if (cfs.containsKey(string)) return cfs.get(string);
		return null;
	}
	
	public boolean isPlayerDead(String name) {
		return deadPlayers.contains(name);
	}
	
	public void addDead(String name) {
		deadPlayers.add(name);
	}
	
	public String getScoreboardName() {
		String s = this.getConfig().getString("scoreboard", "Kill The Patrick");
		return s.substring(0, Math.min(s.length(), 16));
	}


	public boolean inSameTeam(Player pl, Player pl2) {
		return (getTeamForPlayer(pl).equals(getTeamForPlayer(pl2)));
	}
}
