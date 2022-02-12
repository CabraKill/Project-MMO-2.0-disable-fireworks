package harmonised.pmmo.events.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import harmonised.pmmo.api.APIUtils;
import harmonised.pmmo.api.enums.EventType;
import harmonised.pmmo.config.Config;
import harmonised.pmmo.core.Core;
import harmonised.pmmo.features.party.PartyUtils;
import harmonised.pmmo.util.TagUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.IndirectEntityDamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

public class DamageReceivedHandler {

	public static void handle(LivingHurtEvent event) {
		if (event.getEntity() instanceof Player) {			
			Player player = (Player) event.getEntity();
			EventType type = getSourceCategory(event.getSource());
			Core core = Core.get(player.getLevel());
			
			if (!player.level.isClientSide){
				CompoundTag eventHookOutput = core.getEventTriggerRegistry().executeEventListeners(type, event, new CompoundTag());
				if (eventHookOutput.getBoolean(APIUtils.IS_CANCELLED)) 
					event.setCanceled(true);
				else {
					//Process perks
					CompoundTag perkDataIn = eventHookOutput;
					perkDataIn.putFloat(APIUtils.DAMAGE_IN, event.getAmount());
					CompoundTag perkOutput = TagUtils.mergeTags(eventHookOutput, core.getPerkRegistry().executePerk(type, (ServerPlayer) player, perkDataIn));
					if (perkOutput.contains(APIUtils.DAMAGE_OUT))
						event.setAmount(perkOutput.getFloat(APIUtils.DAMAGE_OUT));
					Map<String, Long> xpAward = getExperienceAwards(core, type, event.getSource(), event.getAmount(), player, perkOutput);
					List<ServerPlayer> partyMembersInRange = PartyUtils.getPartyMembersInRange((ServerPlayer) player);
					core.awardXP(partyMembersInRange, xpAward);
				}
			}
		}
	}
	
	private static Map<String, Long> getExperienceAwards(Core core, EventType type, DamageSource source, float damage, Player player, CompoundTag dataIn) {	
		Map<String, Long> mapOut = new HashMap<>();
		switch (type) {
		case FROM_PLAYERS: case FROM_MOBS: case FROM_ANIMALS:{
			core.getExperienceAwards(type, source.getEntity(), player, dataIn).forEach((skill, value) -> {
				mapOut.put(skill, (long)((float)value * damage));
			});			
			break;
		}
		case FROM_ENVIRONMENT: {
			Double value = damage * Config.FROM_ENVIRONMENT_MODIFIER.get();
			Config.FROM_ENVIRONMENT_SKILLS.get().forEach((skill) -> {				
				mapOut.put(skill, value.longValue());
			});
			break;
		}
		case FROM_IMPACT: {
			Double value = damage * Config.FROM_IMPACT_MODIFIER.get();
			Config.FROM_IMPACT_SKILLS.get().forEach((skill) -> {				
				mapOut.put(skill, value.longValue());
			});
			break;
		} 
		case RECEIVE_DAMAGE: {
			Entity uncategorizedEntity = source.getEntity();
			if (uncategorizedEntity != null) 
				core.getExperienceAwards(type, uncategorizedEntity, player, dataIn).forEach((skill, value) -> {
					mapOut.put(skill, (long)((float)value * damage));
				});
			else {
				Double value = damage * Config.RECEIVE_DAMAGE_MODIFIER.get();
				Config.RECEIVE_DAMAGE_SKILLS.get().forEach((skill) -> {					
					mapOut.put(skill, value.longValue());
				});
			}
			break;
		}
		default: {	return new HashMap<>();	}
		}
		return mapOut;
	}
	
	private static final List<String> environmental = List.of(
			DamageSource.IN_FIRE.msgId,
			DamageSource.LIGHTNING_BOLT.msgId,
			DamageSource.ON_FIRE.msgId,
			DamageSource.LAVA.msgId,
			DamageSource.HOT_FLOOR.msgId,
			DamageSource.IN_WALL.msgId,
			DamageSource.CRAMMING.msgId,
			DamageSource.DROWN.msgId,
			DamageSource.STARVE.msgId,
			DamageSource.CACTUS.msgId,
			DamageSource.ANVIL.msgId,
			DamageSource.FALLING_BLOCK.msgId,
			DamageSource.SWEET_BERRY_BUSH.msgId,
			DamageSource.FREEZE.msgId,
			DamageSource.FALLING_STALACTITE.msgId);
	private static final List<String> falling = List.of(
			DamageSource.FALL.msgId,
			DamageSource.STALAGMITE.msgId,
			DamageSource.FLY_INTO_WALL.msgId);
	private static final ResourceLocation MOB_TAG = new ResourceLocation("pmmo:mobs");
	private static final ResourceLocation ANIMAL_TAG = new ResourceLocation("pmmo:animals");
	private static EventType getSourceCategory(DamageSource source) {
		if (source.msgId.equals("player"))
			return EventType.FROM_PLAYERS;
		if (environmental.contains(source.msgId))
			return EventType.FROM_ENVIRONMENT;
		if (falling.contains(source.msgId))
			return EventType.FROM_IMPACT;
		if (EntityTypeTags.getAllTags().getTag(MOB_TAG).contains(source.getEntity().getType()))
			return EventType.FROM_MOBS;
		if (EntityTypeTags.getAllTags().getTag(ANIMAL_TAG).contains(source.getEntity().getType()))
			return EventType.FROM_ANIMALS;
		if (source instanceof IndirectEntityDamageSource)
			return EventType.FROM_PROJECTILES;
		return EventType.RECEIVE_DAMAGE;
	}
}
