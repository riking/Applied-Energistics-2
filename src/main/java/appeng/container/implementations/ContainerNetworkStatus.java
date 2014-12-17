/*
 * This file is part of Applied Energistics 2.
 * Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved.
 *
 * Applied Energistics 2 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Applied Energistics 2 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Applied Energistics 2.  If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.container.implementations;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import appeng.api.AEApi;
import appeng.api.implementations.guiobjects.INetworkTool;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridBlock;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridHost;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.pathing.IPathingGrid;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.GuiSync;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketMEInventoryUpdate;
import appeng.tile.networking.TileController;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import appeng.util.item.AESharedNBT;

public class ContainerNetworkStatus extends AEBaseContainer
{

	IGrid network;

	public ContainerNetworkStatus(InventoryPlayer ip, INetworkTool te) {
		super( ip, null, null );
		IGridHost host = te.getGridHost();

		if ( host != null )
		{
			findNode( host, ForgeDirection.UNKNOWN );
			for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS)
				findNode( host, d );
		}

		if ( network == null && Platform.isServer() )
			isContainerValid = false;
	}

	private void findNode(IGridHost host, ForgeDirection d)
	{
		if ( network == null )
		{
			IGridNode node = host.getGridNode( d );
			if ( node != null )
				network = node.getGrid();
		}
	}

	int delay = 40;

	@GuiSync(0)
	public long avgAddition;
	@GuiSync(1)
	public long powerUsage;
	@GuiSync(2)
	public long currentPower;
	@GuiSync(3)
	public long maxPower;
	@GuiSync(4)
	public int gridStatus; // 0 = ad-hoc, 1 = controller, 2 = conflict, 3 = booting
	@GuiSync(5)
	public long channelCount;
	@GuiSync(6)
	public long channelUse;

	static final AESharedNBT onlineTag = new AESharedNBT( 0 );
	static final AESharedNBT noPowerTag = new AESharedNBT( 0 );
	static final AESharedNBT noChannelTag = new AESharedNBT( 0 );
	static final AESharedNBT[] tagsByStatus = { onlineTag, noPowerTag, noChannelTag };

	static {
		onlineTag.setInteger( "MEStatus", 0 );
		noPowerTag.setInteger( "MEStatus", 1 );
		noChannelTag.setInteger( "MEStatus", 2 );
	}


	@Override
	public void detectAndSendChanges()
	{
		delay++;
		if ( Platform.isServer() && delay > 15 && network != null )
		{
			delay = 0;

			IEnergyGrid eg = network.getCache( IEnergyGrid.class );
			if ( eg != null )
			{
				avgAddition = (long) (100.0 * eg.getAvgPowerInjection());
				powerUsage = (long) (100.0 * eg.getAvgPowerUsage());
				currentPower = (long) (100.0 * eg.getStoredPower());
				maxPower = (long) (100.0 * eg.getMaxStoredPower());
			}

			IPathingGrid paths = network.getCache( IPathingGrid.class );
			if ( paths != null )
			{
				ControllerState controllerState = paths.getControllerState();
				gridStatus = controllerState.ordinal() + 1;
				if (paths.isNetworkBooting()) {
					gridStatus = 0;
				}
				switch (gridStatus) {
				case 1: // ad-hoc
					channelUse = network.getPivot().usedChannels();
					channelCount = network.getPivot().getMaxChannels();
					break;
				case 2: // controller

					// Collect all devices directly attached to controller blocks.
					Set<IGridNode> controllerAttachedDevices = new HashSet<IGridNode>();

					for (IGridNode controllerBlock : network.getMachines( TileController.class ))
					{
						for (IGridConnection connection : controllerBlock.getConnections())
						{
							IGridNode other = connection.getOtherSide( controllerBlock );
							if (!other.hasFlag( GridFlags.CANNOT_CARRY ))
								controllerAttachedDevices.add(other);
						}
					}

					int _channelCount = 0;
					int _channelUse = 0;
					for (IGridNode attached : controllerAttachedDevices)
					{
						_channelCount += attached.getMaxChannels();
						_channelUse += attached.usedChannels();
					}

					channelCount = _channelCount;
					channelUse = _channelUse;
					break;
				default: // conflict, booting
					channelCount = channelUse = 0;
					break;
				}
			}

			PacketMEInventoryUpdate piu;
			try
			{
				piu = new PacketMEInventoryUpdate();

				for (Class<? extends IGridHost> machineClass : network.getMachinesClasses())
				{
					IItemList<IAEItemStack> list = AEApi.instance().storage().createItemList();
					for (IGridNode machine : network.getMachines( machineClass ))
					{
						IGridBlock blk = machine.getGridBlock();
						ItemStack is = blk.getMachineRepresentation();
						if ( is != null && is.getItem() != null )
						{
							IAEItemStack ais = AEItemStack.create( is );
							ais.setStackSize( 1 );

							// Encode power usage in the requestable count
							ais.setCountRequestable( (long) (blk.getIdlePowerUsage() * 100.0) );

							// Encode machine status in the itemstack (this breaks grouping)
							int status = getMachineStatus(machine);

							if ( ais.getTagCompound() != null )
								((AESharedNBT) ais.getTagCompound()).setInteger( "MEStatus", status );
							else
								ais.setTagCompound( (AESharedNBT) AESharedNBT.getSharedTagCompound( tagsByStatus[status].getNBTTagCompoundCopy(), is ) );

							list.add( ais );
						}
					}

					for (IAEItemStack ais : list)
						piu.appendItem( ais );
				}

				for (Object c : this.crafters)
				{
					if ( c instanceof EntityPlayer )
						NetworkHandler.instance.sendTo( piu, (EntityPlayerMP) c );
				}
			}
			catch (IOException e)
			{
				// :P
			}

		}
		super.detectAndSendChanges();
	}

	private int getMachineStatus(IGridNode machine)
	{
		if (machine.isActive())
			return 0;
		if (!machine.meetsChannelRequirements())
			return 2;
		return 1;
	}
}
