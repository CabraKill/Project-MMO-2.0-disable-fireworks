package harmonised.pmmo.network.clientpackets;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import harmonised.pmmo.client.utils.DataMirror;
import harmonised.pmmo.core.Core;
import harmonised.pmmo.util.MsLoggy;
import harmonised.pmmo.util.MsLoggy.LOG_CODE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.network.NetworkEvent;

public class CP_UpdateLevelCache {
	List<Long> levelCache = new ArrayList<>();
	
	public CP_UpdateLevelCache(List<Long> levelCache) {this.levelCache = levelCache;}
	public CP_UpdateLevelCache(FriendlyByteBuf buf) {
		int size = buf.readInt();
		for (int i = 0; i < size; i++) {
			levelCache.add(buf.readLong());
		}
	}
	public void toBytes(FriendlyByteBuf buf) {
		buf.writeInt(levelCache.size());
		for (int i = 0; i < levelCache.size(); i++) {
			buf.writeLong(levelCache.get(i));
		}
	}
	
	public void handle(Supplier<NetworkEvent.Context> ctx) {
		ctx.get().enqueueWork(() -> {
			((DataMirror)Core.get(LogicalSide.CLIENT).getData()).setLevelCache(levelCache);
			MsLoggy.debug(LOG_CODE.XP, "Client Packet Handled for updating levelCache");
		});
		ctx.get().setPacketHandled(true);
	}
}
