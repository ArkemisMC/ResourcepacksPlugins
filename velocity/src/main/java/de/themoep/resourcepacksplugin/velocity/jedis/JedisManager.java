package de.themoep.resourcepacksplugin.velocity.jedis;

import de.themoep.resourcepacksplugin.core.ResourcePack;
import de.themoep.resourcepacksplugin.velocity.VelocityResourcepacks;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPubSub;

public class JedisManager {

    public static JedisManager INSTANCE;

    private final JedisPool pool;
    private final String REDIS_PASSWORD;

    public JedisManager() {
        INSTANCE = this;

        String host = VelocityResourcepacks.INSTANCE.getConfig().getString("jedis.host", "localhost");
        REDIS_PASSWORD = VelocityResourcepacks.INSTANCE.getConfig().getString("jedis.password", null);

        ResourcePack pack = VelocityResourcepacks.INSTANCE.getPackManager().getByName("skyblock");
        if (pack != null) {
            VelocityResourcepacks.INSTANCE.getPackManager().setPackUrl(pack, getURL());
            VelocityResourcepacks.INSTANCE.getPackManager().setPackHash(pack, getHash());
        }

        pool = new JedisPool(host, 6379);

        JedisPubSub jedisPubSub = new JedisPubSub() {
            @Override
            public void onMessage(String channel, String message) {
                if (channel.equalsIgnoreCase("skyblock-pack")) {
                    String[] args = message.split(":");

                    if (args[0].equalsIgnoreCase("reload")) {
                        System.out.println("Reloading pack...");
                        updateHashAndUrl();
                        System.out.println("Pack reloaded from redis!");
                    }
                }
            }
        };

        new Thread(() -> {
            try (Jedis jedis = pool.getResource()) {
                jedis.auth(REDIS_PASSWORD);
                jedis.subscribe(jedisPubSub, "skyblock-pack");
            }
        }).start();
    }

    public String getURL() {
        return getFromRedis("skyblock-pack:url");
    }

    public String getHash() {
        return getFromRedis("skyblock-pack:hash");
    }

    public void updateHashAndUrl() {
        String url = getURL();
        String hash = getHash();

        VelocityResourcepacks.INSTANCE.getPackManager().getPacks().forEach(pack -> {
            if (pack.getName().contains("skyblock")) {
                VelocityResourcepacks.INSTANCE.getPackManager().setPackUrl(pack, url);
                VelocityResourcepacks.INSTANCE.getPackManager().setPackHash(pack, hash);
                System.out.println("Updated pack: " + pack.getName());
            }
        });
    }

    public void onDisable() {
        try {
            pool.close();
        } catch (Exception ignored) {
        }
    }

    public String getFromRedis(String arg0) {
        try (Jedis jedis = pool.getResource()) {
            jedis.auth(REDIS_PASSWORD);
            return jedis.get(arg0);
        }
    }

}
