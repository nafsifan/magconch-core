package org.magconch.command.bot.subcommands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.magconch.command.CommandContext;
import org.magconch.command.bot.BotSubcommand;

public class HelpCommand extends BotSubcommand {

    public HelpCommand() {
        super("help");
    }

    @Override
    public boolean requires(@NotNull CommandSourceStack source) {
        return true;
    }

    @Override
    protected boolean execute(@NotNull CommandContext context) {
        sendHelp(context.getSender());
        return true;
    }

    public static void sendHelp(CommandSender sender) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize(
            "<dark_aqua>======= <aqua><bold>假人系统 (Fake Player) 帮助指南</bold></aqua> =======</dark_aqua>\n"
            + "<gray>点击下方指令可直接粘贴到聊天框：</gray>\n"
            + "<click:suggest_command:'/bot create '><hover:show_text:'<gray>点击填入: 创建假人</gray>'><aqua>/bot create <name> [skin] [world] [x y z]</aqua></hover></click>\n"
            + "  <gray>└ 在当前位置或指定坐标生成假人</gray>\n"
            + "<click:suggest_command:'/bot remove '><hover:show_text:'<gray>点击填入: 移除假人</gray>'><aqua>/bot remove <name></aqua></hover></click>\n"
            + "  <gray>└ 登出并移除属于你的假人</gray>\n"
            + "<click:run_command:'/bot list'><hover:show_text:'<gray>点击执行: 查看在线假人</gray>'><aqua>/bot list</aqua></hover></click>\n"
            + "  <gray>└ 查看当前全服已加载与存档的假人列表</gray>\n"
            + "<click:suggest_command:'/bot action '><hover:show_text:'<gray>点击填入: 下达动作</gray>'><aqua>/bot action <name> start <动作> [参数]</aqua></hover></click>\n"
            + "  <gray>└ 动作: attack(攻击), fish(自动钓鱼), break_block(破坏), use_item_auto(进食), rotation(旋转), move(移动) 等</gray>\n"
            + "<click:suggest_command:'/bot action '><hover:show_text:'<gray>点击填入: 停止动作</gray>'><aqua>/bot action <name> stop <index|all></aqua></hover></click>\n"
            + "  <gray>└ 停止假人正在运行的动作</gray>\n"
            + "<click:suggest_command:'/bot config '><hover:show_text:'<gray>点击填入: 调整配置</gray>'><aqua>/bot config <name> <配置项> <值></aqua></hover></click>\n"
            + "  <gray>└ 配置项: skip_sleep(跳过睡眠), spawn_phantom(幻翼), enable_locator_bar(罗盘方位) 等</gray>\n"
            + "<click:suggest_command:'/bot save '><hover:show_text:'<gray>点击填入: 存档假人</gray>'><aqua>/bot save <name></aqua></hover></click> <gray>| </gray><click:suggest_command:'/bot load '><hover:show_text:'<gray>点击填入: 读档假人</gray>'><aqua>/bot load <name></aqua></hover></click>\n"
            + "  <gray>└ 手动将假人保存下线 / 从存档读回上线</gray>\n"
            + "<dark_gray>提示: 空手右键假人可直接打开 6 行背包 GUI 存取物品；主人长时间不上线假人会自动安全保存退出。</dark_gray>"
        ));
    }
}
