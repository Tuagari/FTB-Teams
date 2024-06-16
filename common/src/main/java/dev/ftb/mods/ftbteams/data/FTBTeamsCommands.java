package dev.ftb.mods.ftbteams.data;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.architectury.platform.Platform;
import dev.ftb.mods.ftbteams.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.property.TeamPropertyArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Predicate;

/**
 * @author LatvianModder
 */
public class FTBTeamsCommands {
	private Predicate<CommandSourceStack> requiresOPorSP() {
		return source -> source.getServer().isSingleplayer() || source.hasPermission(2);
	}

	private RequiredArgumentBuilder<CommandSourceStack, TeamArgumentProvider> teamArg() {
		return Commands.argument("team", TeamArgument.create());
	}

	private String string(CommandContext<?> context, String name) {
		return StringArgumentType.getString(context, name);
	}

	private boolean hasNoParty(CommandSourceStack source) {
		if (source.getEntity() instanceof ServerPlayer) {
			Team team = FTBTeamsAPI.getPlayerTeam(source.getEntity().getUUID());
			return team != null && !team.getType().isParty();
		}

		return false;
	}

	private boolean hasParty(CommandSourceStack source, TeamRank rank) {
		if (source.getEntity() instanceof ServerPlayer) {
			Team team = FTBTeamsAPI.getPlayerTeam(source.getEntity().getUUID());
			return team != null && team.getType().isParty() && team.getHighestRank(source.getEntity().getUUID()).is(rank);
		}

		return false;
	}

	private Team team(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		return FTBTeamsAPI.getPlayerTeam(player);
	}

	private Team teamArg(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		return TeamArgument.get(context, "team");
	}

	private Team teamArg(CommandContext<CommandSourceStack> context, TeamType type) throws CommandSyntaxException {
		Team team = teamArg(context);

		if (team.getType() != type) {
			throw TeamArgument.TEAM_NOT_FOUND.create(team.getName());
		}

		return team;
	}

	private ServerTeam serverTeamArg(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		return (ServerTeam) teamArg(context, TeamType.SERVER);
	}

	private PartyTeam partyTeamArg(CommandContext<CommandSourceStack> context, TeamRank rank) throws CommandSyntaxException {
		PartyTeam team = (PartyTeam) teamArg(context, TeamType.PARTY);

		if (rank != TeamRank.NONE && !team.getHighestRank(context.getSource().getPlayerOrException().getUUID()).is(rank)) {
			throw TeamArgument.NOT_INVITED.create(team.getName());
		}

		return team;
	}

	private PartyTeam team(CommandContext<CommandSourceStack> context, TeamRank rank) throws CommandSyntaxException {
		ServerPlayer player = context.getSource().getPlayerOrException();
		Team team = FTBTeamsAPI.getPlayerTeam(player);

		if (!(team instanceof PartyTeam)) {
			throw TeamArgument.NOT_IN_PARTY.create();
		}

		if (!team.getHighestRank(player.getUUID()).is(rank)) {
			throw TeamArgument.CANT_EDIT.create(team.getName());
		}

		return (PartyTeam) team;
	}

	public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("ftbteams")
				.then(Commands.literal("party")
						.then(Commands.literal("create")
								.requires(this::hasNoParty)
								.then(Commands.argument("name", StringArgumentType.greedyString())
										.executes(ctx -> TeamManager.INSTANCE.createParty(ctx.getSource().getPlayerOrException(), string(ctx, "name")).getLeft())
								)
								.executes(ctx -> TeamManager.INSTANCE.createParty(ctx.getSource().getPlayerOrException(), "").getLeft())
						)
				)
		);

		if (Platform.isDevelopmentEnvironment()) {
			dispatcher.register(Commands.literal("ftbteams_add_fake_player")
					.requires(source -> source.hasPermission(2))
					.then(Commands.argument("profile", GameProfileArgument.gameProfile())
							.executes(ctx -> addFakePlayer(GameProfileArgument.getGameProfiles(ctx, "profile")))
					)
			);
		}
	}

	private int serverId(CommandSourceStack source) {
		MutableComponent component = Component.literal("Server ID: " + FTBTeamsAPI.getManager().getId());
		component.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Click to copy"))));
		component.withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, FTBTeamsAPI.getManager().getId().toString())));
		source.sendSuccess(component, false);
		return 1;
	}

	private int list(CommandSourceStack source, @Nullable TeamType type) {
		MutableComponent list = Component.literal("");

		boolean first = true;

		for (Team team : FTBTeamsAPI.getManager().getTeams()) {
			if (type != null && team.getType() != type) {
				continue;
			}

			if (first) {
				first = false;
			} else {
				list.append(", ");
			}

			list.append(team.getName());
		}

		source.sendSuccess(Component.translatable("ftbteams.list", first ? Component.translatable("ftbteams.info.owner.none") : list), false);
		return Command.SINGLE_SUCCESS;
	}

	private int addFakePlayer(Collection<GameProfile> profiles) {
		for (GameProfile profile : profiles) {
			TeamManager.INSTANCE.playerLoggedIn(null, profile.getId(), profile.getName());
		}

		return 1;
	}
}
