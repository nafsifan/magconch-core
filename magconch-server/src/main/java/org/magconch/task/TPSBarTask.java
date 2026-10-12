package org.magconch.task;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * TPS/MSPT/Ping 状态条异步计算与 MiniMessage 渲染任务
 */
public class TPSBarTask extends BossBarTask {
    private static final String DEFAULT_TITLE = "<gray>TPS<yellow>:</yellow> <tps> MSPT<yellow>:</yellow> <mspt> Ping<yellow>:</yellow> <ping>ms";
    private static final int TICK_INTERVAL = 20;

    private static TPSBarTask instance;
    private double tps = 20.0D;
    private double mspt = 0.0D;
    private int tick = 0;

    public static TPSBarTask instance() {
        if (instance == null) {
            instance = new TPSBarTask();
        }
        return instance;
    }

    @Override
    BossBar createBossBar() {
        return BossBar.bossBar(Component.empty(), 0.0F, getBossBarColor(), BossBar.Overlay.NOTCHED_20);
    }

    @Override
    void updateBossBar(BossBar bossbar, Player player) {
        bossbar.progress(getBossBarProgress());
        bossbar.color(getBossBarColor());
        bossbar.name(MiniMessage.miniMessage().deserialize(
                DEFAULT_TITLE,
                Placeholder.component("tps", getTPSColor()),
                Placeholder.component("mspt", getMSPTColor()),
                Placeholder.component("ping", getPingColor(player.getPing()))
        ));
    }

    @Override
    public void run() {
        if (++tick < TICK_INTERVAL) {
            return;
        }
        tick = 0;

        this.tps = Math.max(Math.min(Bukkit.getTPS()[0], 20.0D), 0.0D);
        this.mspt = Bukkit.getAverageTickTime();

        super.run();
    }

    private float getBossBarProgress() {
        return Math.max(Math.min((float) this.mspt / 50.0F, 1.0F), 0.0F);
    }

    private BossBar.Color getBossBarColor() {
        if (this.mspt < 40.0D) {
            return BossBar.Color.GREEN;
        } else if (this.mspt < 50.0D) {
            return BossBar.Color.YELLOW;
        } else {
            return BossBar.Color.RED;
        }
    }

    private Component getTPSColor() {
        String color;
        if (this.tps >= 19.0D) {
            color = "<green><text>";
        } else if (this.tps >= 15.0D) {
            color = "<yellow><text>";
        } else {
            color = "<red><text>";
        }
        return MiniMessage.miniMessage().deserialize(color, Placeholder.parsed("text", String.format("%.2f", this.tps)));
    }

    private Component getMSPTColor() {
        String color;
        if (this.mspt < 40.0D) {
            color = "<green><text>";
        } else if (this.mspt < 50.0D) {
            color = "<yellow><text>";
        } else {
            color = "<red><text>";
        }
        return MiniMessage.miniMessage().deserialize(color, Placeholder.parsed("text", String.format("%.2f", this.mspt)));
    }

    private Component getPingColor(int ping) {
        String color;
        if (ping < 100) {
            color = "<green><text>";
        } else if (ping < 200) {
            color = "<yellow><text>";
        } else {
            color = "<red><text>";
        }
        return MiniMessage.miniMessage().deserialize(color, Placeholder.parsed("text", String.valueOf(ping)));
    }
}
