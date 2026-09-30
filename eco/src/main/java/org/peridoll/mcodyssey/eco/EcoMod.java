package org.peridoll.mcodyssey.eco;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.text.Text;
import com.mojang.brigadier.arguments.LongArgumentType;
import org.peridoll.mcodyssey.core.CoreMod;
import java.util.concurrent.CompletableFuture;
import static net.minecraft.server.command.CommandManager.*;

public final class EcoMod implements ModInitializer {
    private static Economy economy;
    private static void respond(ServerCommandSource source,CompletableFuture<String> result) {
        result.whenComplete((message,error) -> source.getServer().execute(() -> {
            if(error==null) source.sendFeedback(() -> Text.literal(message),false);
            else {
                Throwable cause=error; while(cause.getCause()!=null) cause=cause.getCause();
                if(cause instanceof IllegalArgumentException) source.sendError(Text.literal(cause.getMessage()));
                else { org.slf4j.LoggerFactory.getLogger("McOdyssey-Eco").error("Economy operation failed",error); source.sendError(Text.literal("DB処理に失敗しました。管理者に連絡してください")); }
            }
        }));
    }
    @Override public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> { economy=new Economy(CoreMod.database()); economy.initialize().join(); });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server) ->
            CoreMod.register(handler.player.getUuid(),handler.player.getGameProfile().getName())
                .thenCompose(v -> economy.balance(handler.player.getUuid())).exceptionally(e -> {
                    org.slf4j.LoggerFactory.getLogger("McOdyssey-Eco").error("Account registration failed",e); return null;
                }));
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment) -> dispatcher.register(literal("mco")
            .then(literal("profile").executes(ctx -> {
                var p=ctx.getSource().getPlayerOrThrow(); var id=p.getUuid(); var name=p.getGameProfile().getName();
                respond(ctx.getSource(),economy.balance(id).thenApply(b -> "McOdyssey | 住民: "+name+" | 所持金: "+b+" MC")); return 1;
            }))
            .then(literal("balance").executes(ctx -> {
                var id=ctx.getSource().getPlayerOrThrow().getUuid(); respond(ctx.getSource(),economy.balance(id).thenApply(b -> "所持金: "+b+" MC")); return 1;
            }))
            .then(literal("pay").then(argument("player",EntityArgumentType.player()).then(argument("amount",LongArgumentType.longArg(1)).executes(ctx -> {
                var from=ctx.getSource().getPlayerOrThrow(); var to=EntityArgumentType.getPlayer(ctx,"player");
                long amount=LongArgumentType.getLong(ctx,"amount"); var targetName=to.getGameProfile().getName();
                respond(ctx.getSource(),economy.pay(from.getUuid(),to.getUuid(),amount).thenApply(v -> targetName+" に "+amount+" MC送金しました")); return 1;
            }))))
            .then(literal("history").executes(ctx -> {
                var id=ctx.getSource().getPlayerOrThrow().getUuid(); respond(ctx.getSource(),economy.history(id).thenApply(lines -> lines.isEmpty()?"取引履歴はありません":String.join("\n",lines))); return 1;
            }))));
    }
}
